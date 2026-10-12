package br.ufv.hashlens.ui.editor

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufv.hashlens.R
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.EditPreview
import br.ufv.hashlens.core.imaging.EditPreviewFactory
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.di.ApplicationScope
import br.ufv.hashlens.di.DefaultDispatcher
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.domain.usecase.CaptureResult
import br.ufv.hashlens.domain.usecase.EditAndRegisterUseCase
import br.ufv.hashlens.domain.usecase.EditResult
import br.ufv.hashlens.domain.usecase.ImportForEditUseCase
import br.ufv.hashlens.domain.usecase.ImportResult
import br.ufv.hashlens.domain.usecase.ImportedParent
import br.ufv.hashlens.domain.usecase.RegistrationProgress
import br.ufv.hashlens.domain.usecase.RetryRegistrationUseCase
import br.ufv.hashlens.ui.capture.RegistrationFailure
import br.ufv.hashlens.ui.capture.RegistrationProgressAction
import br.ufv.hashlens.ui.capture.RegistrationProgressUiState
import br.ufv.hashlens.ui.capture.RegistrationStage
import br.ufv.hashlens.ui.capture.RegistrationWatcher
import br.ufv.hashlens.ui.capture.WatchedStage
import br.ufv.hashlens.ui.capture.toFailure
import br.ufv.hashlens.ui.capture.toStage
import br.ufv.hashlens.ui.capture.walletNotice
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.work.PendingTxReconciler
import br.ufv.hashlens.work.PendingTxScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado do fluxo de edição: o Editor e, depois de "Salvar e registrar", o Acompanhamento.
 *
 * @property progress `null` enquanto o Editor está à vista.
 */
data class EditorFlowUiState(
    val editor: EditorUiState = EditorUiState.Loading,
    val progress: RegistrationProgressUiState? = null
)

sealed interface EditorEffect {
    data object Close : EditorEffect

    data class OpenUrl(val url: String) : EditorEffect

    /** Importar para editar: abre o Photo Picker (US4). */
    data object OpenPicker : EditorEffect

    /** Abre o Resultado da verificação do registro (versão que já estava registrada). */
    data class ViewRecord(val recordId: Long) : EditorEffect

    /** Aviso curto (snackbar); [arg] preenche o `%1$s` do texto. */
    data class Notice(@StringRes val text: Int, val arg: String? = null) : EditorEffect
}

/**
 * Editor → registro da versão → Acompanhamento (T073, ux.md §4.6 e §4.5), para uma imagem do
 * acervo ([PARENT_LOCAL_ID]). Só imagens `CONFIRMED`, cujo arquivo na galeria ainda é o
 * registrado, abrem para edição (T074, US3 cenários 3 e 5).
 *
 * Sem [PARENT_LOCAL_ID], "Importar para editar" (T078, US4): o arquivo vem do Photo Picker e só
 * abre se for cópia exata de um registro ([ImportForEditUseCase]).
 *
 * As operações ficam numa lista com desfazer e refazer; o ajuste em curso aparece na prévia, em
 * resolução reduzida, e só entra na lista ao ser aplicado. O registro roda no [applicationScope],
 * como na captura: sair da tela não cancela a espera pela carteira nem a transação.
 */
// Coordena edição, registro e acompanhamento; as regras de cada ajuste ficam em EditorRules.kt
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val images: LocalImageDao,
    private val gallery: GalleryRepository,
    private val sha256Hasher: Sha256Hasher,
    private val previews: EditPreviewFactory,
    private val editAndRegister: EditAndRegisterUseCase,
    private val importForEdit: ImportForEditUseCase,
    private val retryRegistration: RetryRegistrationUseCase,
    reconciler: PendingTxReconciler,
    reader: RegistryReader,
    private val scheduler: PendingTxScheduler,
    private val session: WalletSession,
    private val config: ChainConfig,
    clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _ui = MutableStateFlow(EditorFlowUiState())
    val ui: StateFlow<EditorFlowUiState> = _ui.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    /** Imagem do acervo a editar; `null` em "Importar para editar", que começa pelo Photo Picker. */
    private val parentLocalId: Long? = savedStateHandle.get<Long>(PARENT_LOCAL_ID)

    /** Pai importado (US4): o registro que o arquivo escolhido reproduz exatamente. */
    private var imported: ImportedParent? = null

    /** Bytes do arquivo escolhido, para "Tentar de novo" sem abrir o seletor outra vez. */
    private var pickedBytes: ByteArray? = null
    private val watcher = RegistrationWatcher(images, reconciler, reader, config, clock)

    private var preview: EditPreview? = null
    private var redo: List<EditOperation> = emptyList()

    /** Operações a renderizar na prévia; a mais recente vence (o controle deslizante muda rápido). */
    private val renderRequests = MutableStateFlow<List<EditOperation>?>(null)

    /** Versão em registro: a imagem no acervo e a carteira que assina. */
    private var editedLocalId: Long? = null
    private var operationWallet: String? = null
    private var tracking: Job? = null

    init {
        if (parentLocalId != null) viewModelScope.launch { open(parentLocalId) } else send(EditorEffect.OpenPicker)
        viewModelScope.launch { renderRequests.filterNotNull().collectLatest(::render) }
        viewModelScope.launch { session.state.collect(::onWalletChanged) }
        addCloseable { preview?.close() }
    }

    fun onAction(action: EditorAction) {
        val editing = editing() ?: return onActionWithoutPhoto(action)
        when (action) {
            EditorAction.Close -> send(EditorEffect.Close)
            // Só existe na importação que falhou, antes de haver foto
            EditorAction.RetryImport -> Unit
            is EditorAction.SelectTool -> selectTool(editing, action.tool)
            is EditorAction.UpdateDraft -> showDraft(editing, action.operation)
            EditorAction.ApplyDraft -> applyDraft(editing)
            EditorAction.CancelDraft -> setOperations(editing.operations)
            EditorAction.Undo -> undo(editing)
            EditorAction.Redo -> redo(editing)
            is EditorAction.Compare -> updateEditing { it.copy(showingOriginal = action.showingOriginal) }
            EditorAction.Save, EditorAction.DismissSave, EditorAction.ConfirmSave -> onSaveAction(editing, action)
        }
    }

    /** Carregando ou não elegível: só fechar ou tentar a importação de novo. */
    private fun onActionWithoutPhoto(action: EditorAction) {
        when (action) {
            EditorAction.Close -> send(EditorEffect.Close)
            EditorAction.RetryImport -> pickedBytes?.let { bytes -> viewModelScope.launch { import(bytes) } }
            else -> Unit
        }
    }

    /** Aviso de permanência: abrir (com alterações), fechar ou confirmar. */
    private fun onSaveAction(editing: EditorUiState.Editing, action: EditorAction) {
        when (action) {
            EditorAction.Save -> if (editing.canSave) updateEditing { it.copy(confirmingSave = true) }
            EditorAction.DismissSave -> updateEditing { it.copy(confirmingSave = false) }
            EditorAction.ConfirmSave -> save(editing)
            else -> Unit
        }
    }

    /** Voltar do sistema: fecha o aviso, depois o ajuste em curso, e só então o Editor. */
    fun onBack() {
        val editing = editing()
        when {
            editing == null || editing.processing -> send(EditorEffect.Close)
            editing.confirmingSave -> onAction(EditorAction.DismissSave)
            editing.draft != null -> onAction(EditorAction.CancelDraft)
            else -> send(EditorEffect.Close)
        }
    }

    fun onProgressAction(action: RegistrationProgressAction) {
        when (action) {
            RegistrationProgressAction.Close, RegistrationProgressAction.BackHome, RegistrationProgressAction.Done ->
                send(EditorEffect.Close)
            RegistrationProgressAction.Retry -> retry()
            // Na edição, "refazer" é voltar ao Editor com as mesmas operações
            RegistrationProgressAction.RetakePhoto -> {
                editedLocalId = null
                _ui.update { it.copy(progress = null) }
                updateEditing { it.copy(processing = false) }
            }
            RegistrationProgressAction.OpenFaucet -> send(EditorEffect.OpenUrl(config.faucetUrl))
            is RegistrationProgressAction.ViewRecord -> send(EditorEffect.ViewRecord(action.recordId))
            is RegistrationProgressAction.OpenUrl -> send(EditorEffect.OpenUrl(action.url))
        }
    }

    /** T074: só abre `CONFIRMED` com o arquivo registrado na galeria; senão, explica por quê. */
    private suspend fun open(localId: Long) {
        val image = images.getById(localId)
        val opened = image?.takeIf { ineligibility(it) == null }?.let { openPreview(it) }
        when {
            opened != null -> startEditing(opened)
            image == null -> showIneligible(IneligibleReason.FileUnavailable)
            else -> showIneligible(ineligibility(image) ?: IneligibleReason.FileUnavailable)
        }
    }

    /** Volta do Photo Picker; fechar sem escolher fecha o Editor. */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) return send(EditorEffect.Close)
        viewModelScope.launch {
            val bytes = try {
                gallery.read(uri)
            } catch (_: IOException) {
                return@launch showIneligible(IneligibleReason.FileUnavailable)
            }
            pickedBytes = bytes
            import(bytes)
        }
    }

    /** US4: só uma cópia exata de um registro com assinatura válida abre no Editor (FR-015). */
    private suspend fun import(bytes: ByteArray) {
        _ui.value = EditorFlowUiState(editor = EditorUiState.Loading)
        when (val result = importForEdit(bytes)) {
            is ImportResult.Eligible -> openImported(result.parent)
            ImportResult.NotRegistered -> showIneligible(IneligibleReason.NotRegistered)
            is ImportResult.Tampered -> showIneligible(IneligibleReason.Tampered)
            is ImportResult.Error -> showIneligible(IneligibleReason.ImportFailed)
        }
    }

    private suspend fun openImported(parent: ImportedParent) {
        val opened = try {
            withContext(defaultDispatcher) { previews.open(parent.bytes) }
        } catch (_: IllegalArgumentException) {
            // Não decodifica: não pode ser uma imagem registrada pelo app
            return showIneligible(IneligibleReason.NotRegistered)
        }
        imported = parent
        // A faixa de autoria só aparece se a original é de outra carteira
        val wallet = (session.state.value as? WalletState.Connected)?.address
        val author = parent.originalRegistrant.takeUnless { it.equals(wallet, ignoreCase = true) }
        startEditing(opened, author)
    }

    private fun startEditing(opened: EditPreview, originalAuthor: String? = null) {
        preview = opened
        _ui.update {
            it.copy(
                editor = EditorUiState.Editing(
                    preview = opened.original,
                    original = opened.original,
                    originalAuthor = originalAuthor
                )
            )
        }
    }

    /** Prévia do arquivo do pai, se ainda for o registrado; `null` se saiu da galeria, mudou ou não decodifica. */
    private suspend fun openPreview(image: LocalImageEntity): EditPreview? = try {
        val bytes = gallery.read(Uri.parse(image.contentUri))
        withContext(defaultDispatcher) {
            bytes.takeIf { sha256Hasher.hash(it.inputStream()).hex == image.sha256Hex }?.let(previews::open)
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun selectTool(editing: EditorUiState.Editing, tool: EditorTool) {
        if (tool.hasParameter) {
            val draft = tool.initialDraft()
            updateEditing { it.copy(activeTool = tool, draft = draft) }
            renderRequests.value = editing.operations + draft
        } else {
            add(editing, tool.instantOperation())
        }
    }

    private fun showDraft(editing: EditorUiState.Editing, draft: EditOperation) {
        if (editing.activeTool == null) return
        updateEditing { it.copy(draft = draft) }
        renderRequests.value = editing.operations + draft
    }

    /** Ajuste sem efeito (brilho 0, contraste 1, recorte inteiro) só fecha o controle. */
    private fun applyDraft(editing: EditorUiState.Editing) {
        val draft = editing.draft ?: return
        if (draft.isIdentity()) setOperations(editing.operations) else add(editing, draft)
    }

    private fun add(editing: EditorUiState.Editing, operation: EditOperation) {
        val operations = editing.operations + operation
        if (!fitsInRecord(operations)) {
            notice(R.string.notice_editor_limit)
            return setOperations(editing.operations)
        }
        redo = emptyList()
        setOperations(operations)
    }

    private fun undo(editing: EditorUiState.Editing) {
        val last = editing.operations.lastOrNull() ?: return
        redo = redo + last
        setOperations(editing.operations.dropLast(1))
    }

    private fun redo(editing: EditorUiState.Editing) {
        val next = redo.lastOrNull() ?: return
        redo = redo.dropLast(1)
        setOperations(editing.operations + next)
    }

    /** Lista confirmada, sem ajuste em curso; a prévia acompanha. */
    private fun setOperations(operations: List<EditOperation>) {
        updateEditing {
            it.copy(
                operations = operations,
                activeTool = null,
                draft = null,
                canUndo = operations.isNotEmpty(),
                canRedo = redo.isNotEmpty()
            )
        }
        renderRequests.value = operations
    }

    private suspend fun render(operations: List<EditOperation>) {
        val current = preview ?: return
        val image = withContext(defaultDispatcher) { current.render(operations) } ?: return
        updateEditing { it.copy(preview = image) }
    }

    /** "Salvar e registrar" no aviso: precisa de carteira na Sepolia, como a captura. */
    private fun save(editing: EditorUiState.Editing) {
        val wallet = (session.state.value as? WalletState.Connected)?.takeIf { it.isSepolia }?.address
        if (wallet == null) {
            updateEditing { it.copy(confirmingSave = false) }
            return notice(R.string.notice_wallet_not_ready)
        }
        operationWallet = wallet
        updateEditing { it.copy(confirmingSave = false, processing = true) }
        val operations = editing.operations
        applicationScope.launch {
            val result = try {
                val parent = imported
                if (parent != null) {
                    editAndRegister.editImported(parent, operations, wallet, ::onProgress)
                } else {
                    editAndRegister(requireNotNull(parentLocalId), operations, wallet, ::onProgress)
                }
            } catch (_: IOException) {
                return@launch showStage(RegistrationStage.Failed(RegistrationFailure.SaveFailed))
            }
            when (result) {
                is EditResult.ParentNotConfirmed -> showIneligible(result.status.toIneligible())
                EditResult.ParentUnavailable -> showIneligible(IneligibleReason.FileUnavailable)
                is EditResult.Registration -> onResult(result.result)
            }
        }
    }

    private fun retry() {
        val id = editedLocalId ?: return
        val wallet =
            (session.state.value as? WalletState.Connected)?.address ?: return notice(R.string.notice_wallet_not_ready)
        operationWallet = wallet
        applicationScope.launch {
            val result = try {
                retryRegistration(id, wallet, ::onProgress)
            } catch (_: IOException) {
                return@launch showStage(RegistrationStage.Failed(RegistrationFailure.SaveFailed))
            }
            onResult(result)
        }
    }

    private fun onProgress(progress: RegistrationProgress) {
        if (progress is RegistrationProgress.Saved) editedLocalId = progress.localId
        showStage(progress.toStage())
    }

    private suspend fun onResult(result: CaptureResult) {
        when (result) {
            is CaptureResult.Submitted -> {
                editedLocalId = result.localId
                showStage(RegistrationStage.Pending)
                scheduler.trackPending()
                track(result.localId)
            }
            is CaptureResult.Failed -> {
                editedLocalId = result.localId
                showStage(RegistrationStage.Failed(result.reason.toFailure()))
            }
            is CaptureResult.InsufficientFunds -> {
                editedLocalId = result.localId
                showStage(result.cost.toStage())
            }
            is CaptureResult.AlreadyRegistered -> showStage(RegistrationStage.AlreadyRegistered(result.recordId))
            is CaptureResult.Confirmed -> images.getById(result.localId)?.let { show(watcher.confirmed(it)) }
            is CaptureResult.WrongWallet -> notice(
                R.string.notice_wrong_wallet_retry,
                AddressFormat.short(result.expected)
            )
        }
    }

    /** Com a tela aberta, acompanha até sair de `PENDING`; ao fechar, quem acompanha é o worker. */
    private fun track(localId: Long) {
        tracking?.cancel()
        val job = applicationScope.launch { watcher.watch(localId)?.let(::show) }
        tracking = job
        addCloseable { job.cancel() }
    }

    /** T058: a carteira mudou enquanto aguardava a aprovação; o pedido aberto não vai passar. */
    private fun onWalletChanged(state: WalletState) {
        val wallet = operationWallet ?: return
        walletNotice(_ui.value.progress?.stage, wallet, state)?.let { send(EditorEffect.Notice(it.text, it.arg)) }
    }

    private fun showStage(stage: RegistrationStage) = show(WatchedStage(stage))

    /** O Acompanhamento mostra a prévia da versão, já com as operações aplicadas. */
    private fun show(watched: WatchedStage) = _ui.update {
        it.copy(
            progress = RegistrationProgressUiState(
                stage = watched.stage,
                thumbnail = (it.editor as? EditorUiState.Editing)?.preview,
                technical = watched.technical
            )
        )
    }

    private fun showIneligible(reason: IneligibleReason) = _ui.update {
        EditorFlowUiState(editor = EditorUiState.NotEligible(reason))
    }

    private fun editing(): EditorUiState.Editing? = _ui.value.editor as? EditorUiState.Editing

    private fun updateEditing(change: (EditorUiState.Editing) -> EditorUiState.Editing) = _ui.update { flow ->
        (flow.editor as? EditorUiState.Editing)?.let { flow.copy(editor = change(it)) } ?: flow
    }

    private fun notice(@StringRes text: Int, arg: String? = null) = send(EditorEffect.Notice(text, arg))

    private fun send(effect: EditorEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    companion object {
        /** Argumento de navegação: a imagem do acervo a editar; sem ele, o Editor importa um arquivo. */
        const val PARENT_LOCAL_ID = "localId"
    }
}
