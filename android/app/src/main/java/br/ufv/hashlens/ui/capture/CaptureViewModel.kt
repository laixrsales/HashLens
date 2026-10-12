package br.ufv.hashlens.ui.capture

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufv.hashlens.R
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.di.ApplicationScope
import br.ufv.hashlens.domain.usecase.CaptureAndRegisterUseCase
import br.ufv.hashlens.domain.usecase.CaptureResult
import br.ufv.hashlens.domain.usecase.RegistrationProgress
import br.ufv.hashlens.domain.usecase.RetryRegistrationUseCase
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.work.PendingTxReconciler
import br.ufv.hashlens.work.PendingTxScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CameraLens { Back, Front }

/**
 * Estado do fluxo de captura: a câmera e, depois da foto, o Acompanhamento do registro.
 *
 * @property progress `null` enquanto a câmera está na tela.
 */
data class CaptureFlowUiState(
    val camera: CaptureUiState = CaptureUiState(),
    val lens: CameraLens = CameraLens.Back,
    val progress: RegistrationProgressUiState? = null
)

sealed interface CaptureEffect {
    data object Close : CaptureEffect

    data class OpenUrl(val url: String) : CaptureEffect

    /** Abre o Resultado da verificação do registro (foto que já estava registrada). */
    data class ViewRecord(val recordId: Long) : CaptureEffect

    /** Aviso curto (snackbar); [arg] preenche o `%1$s` do texto. */
    data class Notice(@StringRes val text: Int, val arg: String? = null) : CaptureEffect
}

/**
 * Câmera → registro → Acompanhamento (T057, ux.md §4.4 e §4.5), com os avisos de carteira durante
 * a operação (T058).
 *
 * O registro roda no [applicationScope]: sair da tela não cancela a espera pela carteira nem a
 * transação. Com a tela aberta, o acompanhamento consulta a rede a cada [POLL_INTERVAL_MS] pelo
 * [PendingTxReconciler]; fora dela, o `PendingTxWorker` segue o mesmo caminho.
 */
// Coordena câmera, registro e acompanhamento; as conversões puras ficam em CaptureMappings.kt
@Suppress("TooManyFunctions")
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val captureAndRegister: CaptureAndRegisterUseCase,
    private val retryRegistration: RetryRegistrationUseCase,
    private val reconciler: PendingTxReconciler,
    private val images: LocalImageDao,
    private val reader: RegistryReader,
    private val session: WalletSession,
    private val scheduler: PendingTxScheduler,
    private val config: ChainConfig,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    private val _ui = MutableStateFlow(CaptureFlowUiState())
    val ui: StateFlow<CaptureFlowUiState> = _ui.asStateFlow()

    private val _effects = Channel<CaptureEffect>(Channel.BUFFERED)
    val effects: Flow<CaptureEffect> = _effects.receiveAsFlow()

    /** Registro em andamento: a imagem no acervo e a carteira que assina. */
    private var localId: Long? = null
    private var operationWallet: String? = null
    private var tracking: Job? = null

    /** Miniatura da foto do registro em andamento, para o Acompanhamento. */
    private var thumbnailOfRun: ImageBitmap? = null

    private val watcher = RegistrationWatcher(images, reconciler, reader, config, clock)

    init {
        viewModelScope.launch { session.state.collect(::onWalletChanged) }
    }

    /** Antes do disparo: a foto só é tirada com uma carteira conectada na Sepolia. */
    fun canTakePhoto(): Boolean {
        val ready = (session.state.value as? WalletState.Connected)?.isSepolia == true
        if (!ready) notice(R.string.notice_wallet_not_ready)
        return ready
    }

    fun onCameraAction(action: CaptureAction) {
        when (action) {
            CaptureAction.ToggleFlash -> updateCamera { it.copy(flash = it.flash.next()) }
            CaptureAction.SwitchCamera ->
                _ui.update { it.copy(lens = if (it.lens == CameraLens.Back) CameraLens.Front else CameraLens.Back) }
            CaptureAction.Retry -> updateCamera { it.copy(camera = CameraState.Ready) }
            CaptureAction.Close -> send(CaptureEffect.Close)
            // Disparo e permissão dependem da câmera e da activity: ficam com a rota
            CaptureAction.TakePhoto, CaptureAction.PermissionAction -> Unit
        }
    }

    fun onCameraError() = updateCamera { it.copy(camera = CameraState.Error) }

    fun onCameraBound(canSwitchCamera: Boolean) = updateCamera { it.copy(canSwitchCamera = canSwitchCamera) }

    /** Foto tirada: codifica, grava, confere e registra, mostrando cada etapa. */
    fun onPhotoTaken(photo: Bitmap, rotationDegrees: Int, thumbnail: ImageBitmap?) {
        val wallet = (session.state.value as? WalletState.Connected)?.address ?: return
        operationWallet = wallet
        updateCamera { it.copy(camera = CameraState.Processing) }
        _ui.update { it.copy(progress = null) }
        thumbnailOfRun = thumbnail
        run { captureAndRegister(photo, rotationDegrees, wallet, ::onProgress) }
    }

    fun onProgressAction(action: RegistrationProgressAction) {
        when (action) {
            RegistrationProgressAction.Close, RegistrationProgressAction.BackHome, RegistrationProgressAction.Done ->
                send(CaptureEffect.Close)
            RegistrationProgressAction.Retry -> retry()
            RegistrationProgressAction.RetakePhoto -> {
                localId = null
                _ui.update { it.copy(progress = null, camera = it.camera.copy(camera = CameraState.Ready)) }
            }
            RegistrationProgressAction.OpenFaucet -> send(CaptureEffect.OpenUrl(config.faucetUrl))
            is RegistrationProgressAction.ViewRecord -> send(CaptureEffect.ViewRecord(action.recordId))
            is RegistrationProgressAction.OpenUrl -> send(CaptureEffect.OpenUrl(action.url))
        }
    }

    private fun retry() {
        val id = localId ?: return
        val wallet = (session.state.value as? WalletState.Connected)?.address
        if (wallet == null) {
            notice(R.string.notice_wallet_not_ready)
            return
        }
        operationWallet = wallet
        run { retryRegistration(id, wallet, ::onProgress) }
    }

    /** Roda o registro fora da tela e mostra o resultado; falha na galeria pede outra foto. */
    private fun run(block: suspend () -> CaptureResult) {
        applicationScope.launch {
            val result = try {
                block()
            } catch (_: IOException) {
                null
            }
            if (result ==
                null
            ) {
                showStage(RegistrationStage.Failed(RegistrationFailure.SaveFailed))
            } else {
                onResult(result)
            }
        }
    }

    private fun onProgress(progress: RegistrationProgress) {
        if (progress is RegistrationProgress.Saved) localId = progress.localId
        showStage(progress.toStage())
    }

    private suspend fun onResult(result: CaptureResult) {
        when (result) {
            is CaptureResult.Submitted -> {
                localId = result.localId
                showStage(RegistrationStage.Pending)
                scheduler.trackPending()
                track(result.localId)
            }
            is CaptureResult.Failed -> {
                localId = result.localId
                showStage(RegistrationStage.Failed(result.reason.toFailure()))
            }
            is CaptureResult.InsufficientFunds -> {
                localId = result.localId
                showStage(result.cost.toStage())
            }
            is CaptureResult.AlreadyRegistered -> showStage(RegistrationStage.AlreadyRegistered(result.recordId))
            is CaptureResult.Confirmed -> images.getById(result.localId)?.let { confirmed(it) }
            is CaptureResult.WrongWallet ->
                notice(R.string.notice_wrong_wallet_retry, AddressFormat.short(result.expected))
        }
    }

    /**
     * Com a tela aberta, consulta a rede até o registro sair de `PENDING`. Roda no escopo da
     * aplicação, mas é cancelado quando a tela fecha: dali em diante, quem acompanha é o worker.
     */
    private fun track(localId: Long) {
        tracking?.cancel()
        val job = applicationScope.launch { watcher.watch(localId)?.let(::show) }
        tracking = job
        addCloseable { job.cancel() }
    }

    private suspend fun confirmed(image: LocalImageEntity) = show(watcher.confirmed(image))

    private fun show(watched: WatchedStage) = _ui.update {
        it.copy(
            progress = RegistrationProgressUiState(
                stage = watched.stage,
                thumbnail = thumbnailOfRun,
                technical = watched.technical
            )
        )
    }

    /** T058: a carteira mudou enquanto aguardava a aprovação; o pedido aberto não vai passar. */
    private fun onWalletChanged(state: WalletState) {
        val wallet = operationWallet ?: return
        walletNotice(_ui.value.progress?.stage, wallet, state)?.let(::send)
    }

    private fun showStage(stage: RegistrationStage) {
        _ui.update {
            it.copy(
                camera = it.camera.copy(camera = CameraState.Ready),
                progress = RegistrationProgressUiState(stage = stage, thumbnail = thumbnailOfRun)
            )
        }
    }

    private fun updateCamera(change: (CaptureUiState) -> CaptureUiState) = _ui.update {
        it.copy(camera = change(it.camera))
    }

    private fun notice(@StringRes text: Int, arg: String? = null) = send(CaptureEffect.Notice(text, arg))

    private fun send(effect: CaptureEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    companion object {
        /** Um bloco da Sepolia sai a cada ~12 s. */
        const val POLL_INTERVAL_MS = RegistrationWatcher.POLL_INTERVAL_MS
    }
}
