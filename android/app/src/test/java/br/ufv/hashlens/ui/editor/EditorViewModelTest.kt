package br.ufv.hashlens.ui.editor

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import br.ufv.hashlens.R
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.EditPreview
import br.ufv.hashlens.core.imaging.EditPreviewFactory
import br.ufv.hashlens.core.metrics.Metric
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.domain.usecase.CaptureResult
import br.ufv.hashlens.domain.usecase.EditAndRegisterUseCase
import br.ufv.hashlens.domain.usecase.EditResult
import br.ufv.hashlens.domain.usecase.ImportForEditUseCase
import br.ufv.hashlens.domain.usecase.ImportResult
import br.ufv.hashlens.domain.usecase.ImportedParent
import br.ufv.hashlens.domain.usecase.RetryRegistrationUseCase
import br.ufv.hashlens.testing.MutableClock
import br.ufv.hashlens.testing.RecordingMetrics
import br.ufv.hashlens.ui.capture.RegistrationProgressAction
import br.ufv.hashlens.ui.capture.RegistrationStage
import br.ufv.hashlens.work.PendingTxReconciler
import br.ufv.hashlens.work.PendingTxScheduler
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * T073 e T074: Editor → registro da versão (ux.md §4.6). A prévia (OpenCV nativo), a galeria, o
 * acervo, o caso de uso e a carteira são falsos; o SHA-256 é o real. Cada lista de operações tem
 * a sua imagem de prévia, para conferir qual lista foi renderizada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val walletState = MutableStateFlow<WalletState>(CONNECTED)
    private val session = mockk<WalletSession> { every { state } returns walletState }
    private val images = mockk<LocalImageDao>()
    private val gallery = mockk<GalleryRepository>()
    private val previews = mockk<EditPreviewFactory>()
    private val preview = mockk<EditPreview>(relaxUnitFun = true)
    private val editAndRegister = mockk<EditAndRegisterUseCase>()
    private val importForEdit = mockk<ImportForEditUseCase>()
    private val retryRegistration = mockk<RetryRegistrationUseCase>()
    private val reconciler = mockk<PendingTxReconciler>(relaxUnitFun = true)
    private val reader = mockk<RegistryReader>()
    private var tracked = 0
    private val scheduler = PendingTxScheduler { tracked++ }
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")
    private val clock = MutableClock(NOW)
    private val metrics = RecordingMetrics()

    private val original = mockk<ImageBitmap>()
    private val rendered = mutableMapOf<List<EditOperation>, ImageBitmap>()

    /** Imagem de prévia de [operations], a mesma a cada chamada. */
    private fun renderOf(vararg operations: EditOperation): ImageBitmap =
        rendered.getOrPut(operations.toList()) { mockk() }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Teste JVM sem Robolectric: o Uri do Android é só um stub
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()
        coEvery { images.getById(PARENT_ID) } returns parent()
        coEvery { gallery.read(any()) } returns BYTES
        every { previews.open(BYTES) } returns preview
        every { preview.original } returns original
        every { preview.render(any()) } answers {
            val operations = firstArg<List<EditOperation>>()
            if (operations.isEmpty()) original else renderOf(*operations.toTypedArray())
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }

    private fun parent(status: RegistrationStatus = RegistrationStatus.CONFIRMED, sha256Hex: String = SHA) =
        LocalImageEntity(
            localId = PARENT_ID,
            contentUri = "content://media/external/images/media/10",
            sha256Hex = sha256Hex,
            pHashHex = "c3d2e1f0a9b8c7d6",
            origin = ImageOrigin.CAPTURE,
            walletAddress = ADDRESS.lowercase(),
            status = status,
            recordId = if (status == RegistrationStatus.CONFIRMED) 1L else null,
            createdAt = NOW.toEpochMilli()
        )

    /** @param localId `null` abre o Editor em "Importar para editar". */
    private fun TestScope.viewModel(localId: Long? = PARENT_ID) = EditorViewModel(
        images = images,
        gallery = gallery,
        sha256Hasher = Sha256Hasher(),
        previews = previews,
        editAndRegister = editAndRegister,
        importForEdit = importForEdit,
        retryRegistration = retryRegistration,
        reconciler = reconciler,
        reader = reader,
        scheduler = scheduler,
        session = session,
        config = config,
        clock = clock,
        metrics = metrics,
        applicationScope = backgroundScope,
        defaultDispatcher = dispatcher,
        savedStateHandle = SavedStateHandle(localId?.let { mapOf(EditorViewModel.PARENT_LOCAL_ID to it) } ?: emptyMap())
    )

    private fun TestScope.effects(viewModel: EditorViewModel): List<EditorEffect> {
        val collected = mutableListOf<EditorEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { collected += it } }
        return collected
    }

    private fun EditorViewModel.editing() = ui.value.editor.shouldBeInstanceOf<EditorUiState.Editing>()

    private fun EditorViewModel.act(vararg actions: EditorAction) = actions.forEach(::onAction)

    // T074: elegibilidade

    @Test
    fun `imagem confirmada com o arquivo registrado abre no Editor sem alterações`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.editing().run {
            preview shouldBeSameInstanceAs original
            this.original shouldBeSameInstanceAs this@EditorViewModelTest.original
            operations shouldBe emptyList()
            canSave shouldBe false
        }
    }

    @ParameterizedTest
    @EnumSource(RegistrationStatus::class, names = ["LOCAL_ONLY", "AWAITING_WALLET", "PENDING"])
    fun `registro ainda não confirmado não abre e explica que está sendo registrado`(status: RegistrationStatus) =
        runTest(dispatcher) {
            coEvery { images.getById(PARENT_ID) } returns parent(status)

            val viewModel = viewModel()

            viewModel.ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.Pending)
            coVerify(exactly = 0) { gallery.read(any()) }
        }

    @Test
    fun `registro que falhou não abre e explica que precisa ser refeito`() = runTest(dispatcher) {
        coEvery { images.getById(PARENT_ID) } returns parent(RegistrationStatus.FAILED)

        viewModel().ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.RegistrationFailed)
    }

    @Test
    fun `arquivo alterado, removido ou imagem fora do acervo não abrem`() = runTest(dispatcher) {
        coEvery { images.getById(PARENT_ID) } returns parent(sha256Hex = "0".repeat(64))
        viewModel().ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.FileUnavailable)

        coEvery { images.getById(PARENT_ID) } returns parent()
        coEvery { gallery.read(any()) } throws FileNotFoundException("removido")
        viewModel().ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.FileUnavailable)

        coEvery { images.getById(PARENT_ID) } returns null
        viewModel().ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.FileUnavailable)

        verify(exactly = 0) { previews.open(any()) }
    }

    @Test
    fun `fechar funciona também na imagem não elegível`() = runTest(dispatcher) {
        coEvery { images.getById(PARENT_ID) } returns parent(RegistrationStatus.PENDING)
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(EditorAction.Close)

        effects shouldBe listOf(EditorEffect.Close)
    }

    // T073: filtros, ajustes e histórico

    @Test
    fun `filtro sem parâmetro é aplicado com um toque e entra na prévia`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale), EditorAction.SelectTool(EditorTool.Rotate))

        viewModel.editing().run {
            operations shouldBe listOf(EditOperation.Grayscale, EditOperation.Rotate90(1))
            preview shouldBeSameInstanceAs renderOf(EditOperation.Grayscale, EditOperation.Rotate90(1))
            canUndo shouldBe true
            canSave shouldBe true
        }
    }

    @Test
    fun `ajuste com parâmetro aparece na prévia e só entra na lista ao aplicar`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.act(EditorAction.SelectTool(EditorTool.Brightness))
        viewModel.editing().run {
            activeTool shouldBe EditorTool.Brightness
            draft shouldBe EditOperation.Brightness(0)
        }

        viewModel.act(EditorAction.UpdateDraft(EditOperation.Brightness(30)))
        viewModel.editing().run {
            operations shouldBe emptyList()
            preview shouldBeSameInstanceAs renderOf(EditOperation.Brightness(30))
            canSave shouldBe false
        }

        viewModel.act(EditorAction.ApplyDraft)
        viewModel.editing().run {
            operations shouldBe listOf(EditOperation.Brightness(30))
            draft.shouldBeNull()
            activeTool.shouldBeNull()
            canSave shouldBe true
        }
    }

    @Test
    fun `cancelar o ajuste volta a prévia às operações aplicadas`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale), EditorAction.SelectTool(EditorTool.Blur))
        viewModel.act(EditorAction.UpdateDraft(EditOperation.Blur(9)))

        viewModel.act(EditorAction.CancelDraft)

        viewModel.editing().run {
            operations shouldBe listOf(EditOperation.Grayscale)
            draft.shouldBeNull()
            preview shouldBeSameInstanceAs renderOf(EditOperation.Grayscale)
        }
    }

    @Test
    fun `ajuste sem efeito não entra na lista`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.act(EditorAction.SelectTool(EditorTool.Contrast), EditorAction.ApplyDraft)
        viewModel.act(EditorAction.SelectTool(EditorTool.Crop), EditorAction.ApplyDraft)

        viewModel.editing().operations shouldBe emptyList()
    }

    @Test
    fun `bordas começam com sensibilidade 50 e o limiar superior é o triplo`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.act(EditorAction.SelectTool(EditorTool.Edges))

        viewModel.editing().draft shouldBe EditOperation.Edges(50, 150)
        edgesOf(100) shouldBe EditOperation.Edges(100, 255)
    }

    @Test
    fun `desfazer e refazer andam na lista e uma operação nova apaga o refazer`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale), EditorAction.SelectTool(EditorTool.Sepia))

        viewModel.act(EditorAction.Undo)
        viewModel.editing().run {
            operations shouldBe listOf(EditOperation.Grayscale)
            canRedo shouldBe true
        }

        viewModel.act(EditorAction.Redo)
        viewModel.editing().operations shouldBe listOf(EditOperation.Grayscale, EditOperation.Sepia)

        viewModel.act(EditorAction.Undo, EditorAction.SelectTool(EditorTool.Sharpen))
        viewModel.editing().run {
            operations shouldBe listOf(EditOperation.Grayscale, EditOperation.Sharpen)
            canRedo shouldBe false
        }

        viewModel.act(EditorAction.Undo, EditorAction.Undo)
        viewModel.editing().run {
            operations shouldBe emptyList()
            canUndo shouldBe false
            preview shouldBeSameInstanceAs original
        }
    }

    @Test
    fun `operação que não cabe nos 256 bytes do registro não entra e avisa`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)
        // "grayscale;" tem 10 bytes: 25 cabem (249 bytes), a 26ª passaria do limite
        repeat(26) { viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale)) }

        viewModel.editing().operations.size shouldBe 25
        effects shouldBe listOf(EditorEffect.Notice(R.string.notice_editor_limit))
    }

    @Test
    fun `tocar e segurar mostra a original até soltar`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.act(EditorAction.Compare(showingOriginal = true))
        viewModel.editing().showingOriginal shouldBe true

        viewModel.act(EditorAction.Compare(showingOriginal = false))
        viewModel.editing().showingOriginal shouldBe false
    }

    @Test
    fun `voltar fecha o aviso, depois o ajuste e só então o Editor`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale), EditorAction.Save)

        viewModel.onBack()
        viewModel.editing().confirmingSave shouldBe false

        viewModel.act(EditorAction.SelectTool(EditorTool.Brightness))
        viewModel.onBack()
        viewModel.editing().draft.shouldBeNull()
        effects shouldBe emptyList()

        viewModel.onBack()
        effects shouldBe listOf(EditorEffect.Close)
    }

    // T073: salvar e registrar

    @Test
    fun `salvar sem alterações não abre o aviso`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.act(EditorAction.Save)

        viewModel.editing().confirmingSave shouldBe false
    }

    @Test
    fun `salvar abre o aviso de permanência e continuar editando o fecha`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale))

        viewModel.act(EditorAction.Save)
        viewModel.editing().confirmingSave shouldBe true

        viewModel.act(EditorAction.DismissSave)
        viewModel.editing().confirmingSave shouldBe false
        coVerify(exactly = 0) { editAndRegister(any(), any(), any(), any()) }
    }

    @Test
    fun `sem carteira na Sepolia, salvar avisa e não registra`() = runTest(dispatcher) {
        walletState.value = WalletState.Connected(ADDRESS, chainId = 1)
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale), EditorAction.Save)

        viewModel.act(EditorAction.ConfirmSave)

        effects shouldBe listOf(EditorEffect.Notice(R.string.notice_wallet_not_ready))
        viewModel.editing().processing shouldBe false
        coVerify(exactly = 0) { editAndRegister(any(), any(), any(), any()) }
    }

    @Test
    fun `mede de Salvar e registrar até a transação aprovada e enviada`() = runTest(dispatcher) {
        coEvery { editAndRegister(PARENT_ID, any(), ADDRESS, any()) } coAnswers {
            clock.advance(Duration.ofSeconds(11))
            EditResult.Registration(CaptureResult.Submitted(localId = EDITED_ID, txHash = TX))
        }
        coEvery { images.getById(EDITED_ID) } returns parent(RegistrationStatus.PENDING).copy(localId = EDITED_ID)
        val viewModel = viewModel()
        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale), EditorAction.Save)

        viewModel.act(EditorAction.ConfirmSave)

        metrics.records shouldBe
            listOf(RecordingMetrics.Record(Metric.EDIT_TO_SUBMITTED, Duration.ofSeconds(11), "Submitted"))
    }

    @Test
    fun `confirmar registra a versão com as operações e mostra o Acompanhamento`() = runTest(dispatcher) {
        val operations = listOf(EditOperation.Grayscale, EditOperation.Brightness(30))
        coEvery { editAndRegister(PARENT_ID, operations, ADDRESS, any()) } returns
            EditResult.Registration(CaptureResult.Submitted(localId = EDITED_ID, txHash = TX))
        coEvery { images.getById(EDITED_ID) } returns parent(RegistrationStatus.PENDING).copy(localId = EDITED_ID)
        val viewModel = viewModel()
        viewModel.act(
            EditorAction.SelectTool(EditorTool.Grayscale),
            EditorAction.SelectTool(EditorTool.Brightness),
            EditorAction.UpdateDraft(EditOperation.Brightness(30)),
            EditorAction.ApplyDraft,
            EditorAction.Save
        )

        viewModel.act(EditorAction.ConfirmSave)

        val progress = viewModel.ui.value.progress.shouldNotBeNull()
        progress.stage shouldBe RegistrationStage.Pending
        // A miniatura do Acompanhamento é a prévia da versão
        progress.thumbnail shouldBeSameInstanceAs renderOf(*operations.toTypedArray())
        tracked shouldBe 1
        coVerify(exactly = 1) { editAndRegister(PARENT_ID, operations, ADDRESS, any()) }
    }

    @Test
    fun `pai que deixou de estar confirmado ao salvar mostra a explicação`() = runTest(dispatcher) {
        coEvery { editAndRegister(any(), any(), any(), any()) } returns
            EditResult.ParentNotConfirmed(RegistrationStatus.FAILED)
        val viewModel = viewModel()
        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale), EditorAction.Save, EditorAction.ConfirmSave)

        viewModel.ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.RegistrationFailed)
        viewModel.ui.value.progress.shouldBeNull()
    }

    @Test
    fun `versão já registrada leva ao Resultado do registro`() = runTest(dispatcher) {
        coEvery { editAndRegister(any(), any(), any(), any()) } returns
            EditResult.Registration(CaptureResult.AlreadyRegistered(9))
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.act(EditorAction.SelectTool(EditorTool.Grayscale), EditorAction.Save, EditorAction.ConfirmSave)

        viewModel.ui.value.progress?.stage shouldBe RegistrationStage.AlreadyRegistered(9)
        viewModel.onProgressAction(RegistrationProgressAction.ViewRecord(9))

        effects shouldBe listOf(EditorEffect.ViewRecord(9))
    }

    // T078: importar para editar (US4)

    private fun imported(originalRegistrant: String = OTHER) = ImportedParent(
        recordId = 7,
        originalId = 1,
        registrant = OTHER,
        originalRegistrant = originalRegistrant,
        bytes = BYTES
    )

    @Test
    fun `importar abre o Photo Picker e fechar sem escolher fecha o Editor`() = runTest(dispatcher) {
        val viewModel = viewModel(localId = null)
        val effects = effects(viewModel)

        viewModel.onImagePicked(null)

        viewModel.ui.value.editor shouldBe EditorUiState.Loading
        effects shouldBe listOf(EditorEffect.OpenPicker, EditorEffect.Close)
    }

    @Test
    fun `cópia exata de um registro de outra pessoa abre com a faixa do autor da original`() = runTest(dispatcher) {
        coEvery { importForEdit(BYTES) } returns ImportResult.Eligible(imported())
        val viewModel = viewModel(localId = null)

        viewModel.onImagePicked(mockk())

        viewModel.editing().run {
            originalAuthor shouldBe OTHER
            preview shouldBeSameInstanceAs original
        }
    }

    @Test
    fun `cópia exata de uma original da própria carteira abre sem a faixa de autoria`() = runTest(dispatcher) {
        coEvery { importForEdit(BYTES) } returns
            ImportResult.Eligible(imported(originalRegistrant = ADDRESS.lowercase()))
        val viewModel = viewModel(localId = null)

        viewModel.onImagePicked(mockk())

        viewModel.editing().originalAuthor.shouldBeNull()
    }

    @Test
    fun `arquivo sem registro exato ou adulterado é recusado com a explicação`() = runTest(dispatcher) {
        coEvery { importForEdit(BYTES) } returns ImportResult.NotRegistered
        val viewModel = viewModel(localId = null)
        viewModel.onImagePicked(mockk())
        viewModel.ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.NotRegistered)

        coEvery { importForEdit(BYTES) } returns ImportResult.Tampered(recordId = 7)
        val other = viewModel(localId = null)
        other.onImagePicked(mockk())
        other.ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.Tampered)
        verify(exactly = 0) { previews.open(any()) }
    }

    @Test
    fun `sem conexão, tentar de novo confere o mesmo arquivo sem abrir o seletor`() = runTest(dispatcher) {
        coEvery { importForEdit(BYTES) } returns ImportResult.Error(Reason.NETWORK)
        val viewModel = viewModel(localId = null)
        viewModel.onImagePicked(mockk())
        viewModel.ui.value.editor shouldBe EditorUiState.NotEligible(IneligibleReason.ImportFailed)

        coEvery { importForEdit(BYTES) } returns ImportResult.Eligible(imported())
        viewModel.onAction(EditorAction.RetryImport)

        viewModel.editing().originalAuthor shouldBe OTHER
        coVerify(exactly = 1) { gallery.read(any()) }
    }

    @Test
    fun `salvar a edição importada registra com o pai importado`() = runTest(dispatcher) {
        val parent = imported()
        coEvery { importForEdit(BYTES) } returns ImportResult.Eligible(parent)
        coEvery { editAndRegister.editImported(parent, listOf(EditOperation.Sepia), ADDRESS, any()) } returns
            EditResult.Registration(CaptureResult.AlreadyRegistered(9))
        val viewModel = viewModel(localId = null)
        viewModel.onImagePicked(mockk())

        viewModel.act(EditorAction.SelectTool(EditorTool.Sepia), EditorAction.Save, EditorAction.ConfirmSave)

        viewModel.ui.value.progress?.stage shouldBe RegistrationStage.AlreadyRegistered(9)
        coVerify(exactly = 0) { editAndRegister(any(), any(), any(), any()) }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-11T18:00:00Z")
        const val CONTRACT = "0xF2f42B34414936e456c6AC10c4844f456CAD65Ba"
        const val ADDRESS = "0x1234567890AbcdEF1234567890aBcdef12345678"
        val CONNECTED = WalletState.Connected(ADDRESS, chainId = ChainConfig.SEPOLIA_CHAIN_ID)
        const val PARENT_ID = 10L
        const val EDITED_ID = 11L
        const val OTHER = "0xABCDEFabcdef0123456789ABCDEFabcdef012345"
        const val TX = "0x5555555555555555555555555555555555555555555555555555555555555555"
        val BYTES: ByteArray = "bytes-do-pai".encodeToByteArray()
        val SHA: String = MessageDigest.getInstance("SHA-256").digest(BYTES).toHexString()
    }
}
