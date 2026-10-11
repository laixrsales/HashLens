package br.ufv.hashlens.ui.verify

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import br.ufv.hashlens.core.imaging.ImageDecoder
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.domain.model.VerificationResult
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.domain.usecase.VerifyImageUseCase
import br.ufv.hashlens.ui.preview.SampleData.Records
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T065: Verificar imagem → Resultado (ux.md §4.7 e §4.8). O caso de uso, a galeria e o decodificador
 * da miniatura são falsos; o decodificador recusa os bytes, então as miniaturas ficam `null`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val verifyImage = mockk<VerifyImageUseCase>()
    private val gallery = mockk<GalleryRepository>()
    private val decoder = mockk<ImageDecoder> {
        every { decode(any()) } throws IllegalArgumentException("sem decodificador na JVM")
    }
    private val uri = mockk<Uri>()

    @BeforeEach
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel(recordId: Long? = null) = VerifyViewModel(
        verifyImage = verifyImage,
        gallery = gallery,
        decoder = decoder,
        defaultDispatcher = dispatcher,
        savedStateHandle = SavedStateHandle(recordId?.let { mapOf(VerifyViewModel.RECORD_ID to it) } ?: emptyMap())
    )

    private fun TestScope.effects(viewModel: VerifyViewModel): List<VerifyEffect> {
        val collected = mutableListOf<VerifyEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { collected += it } }
        return collected
    }

    /** A galeria devolve [BYTES] e o caso de uso avisa as consultas antes de responder [result]. */
    private fun verifying(result: VerificationResult, onQuerying: () -> Unit = {}) {
        coEvery { gallery.read(uri) } returns BYTES
        coEvery { verifyImage(BYTES, any()) } coAnswers {
            secondArg<() -> Unit>()()
            onQuerying()
            result
        }
    }

    @Test
    fun `começa em Verificar imagem e o botão abre o Photo Picker`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onVerifyAction(VerifyAction.ChooseImage)

        viewModel.ui.value shouldBe VerifyFlowUiState()
        effects shouldBe listOf(VerifyEffect.OpenPicker)
    }

    @Test
    fun `fechar o Photo Picker sem escolher não muda nada`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onImagePicked(null)

        viewModel.ui.value shouldBe VerifyFlowUiState()
        coVerify(exactly = 0) { gallery.read(any()) }
    }

    @Test
    fun `imagem escolhida passa por consultando registros e mostra o resultado`() = runTest(dispatcher) {
        val viewModel = viewModel()
        var duringQuery: VerifyFlowUiState? = null
        val result = VerificationResult.RegisteredOriginal(Records.original)
        verifying(result) { duringQuery = viewModel.ui.value }

        viewModel.onImagePicked(uri)

        duringQuery shouldBe VerifyFlowUiState(verify = VerifyUiState.Analyzing(AnalysisStep.Querying))
        viewModel.ui.value shouldBe VerifyFlowUiState(result = VerificationResultUiState(result))
    }

    @Test
    fun `arquivo não suportado fica em Verificar imagem`() = runTest(dispatcher) {
        val viewModel = viewModel()
        verifying(VerificationResult.Error(Reason.UNSUPPORTED_FILE))

        viewModel.onImagePicked(uri)

        viewModel.ui.value shouldBe VerifyFlowUiState(verify = VerifyUiState.UnsupportedFile)
    }

    @Test
    fun `arquivo que não pode ser lido fica em Verificar imagem como não suportado`() = runTest(dispatcher) {
        val viewModel = viewModel()
        coEvery { gallery.read(uri) } throws FileNotFoundException("removido")

        viewModel.onImagePicked(uri)

        viewModel.ui.value shouldBe VerifyFlowUiState(verify = VerifyUiState.UnsupportedFile)
        coVerify(exactly = 0) { verifyImage(any(), any()) }
    }

    @Test
    fun `sem conexão, tentar de novo verifica a mesma imagem sem abrir o seletor`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)
        verifying(VerificationResult.Error(Reason.NETWORK))
        viewModel.onImagePicked(uri)
        val result = VerificationResult.NotRegistered
        verifying(result)

        viewModel.onResultAction(VerificationResultAction.Retry)

        viewModel.ui.value shouldBe VerifyFlowUiState(result = VerificationResultUiState(result))
        coVerify(exactly = 1) { gallery.read(uri) }
        effects shouldBe emptyList()
    }

    @Test
    fun `verificar outra imagem volta ao início e abre o Photo Picker`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)
        verifying(VerificationResult.NotRegistered)
        viewModel.onImagePicked(uri)

        viewModel.onResultAction(VerificationResultAction.VerifyAnother)

        viewModel.ui.value shouldBe VerifyFlowUiState()
        effects shouldBe listOf(VerifyEffect.OpenPicker)
    }

    @Test
    fun `histórico, link e voltar viram efeitos para a rota`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onResultAction(VerificationResultAction.ViewHistory(2))
        viewModel.onResultAction(VerificationResultAction.OpenUrl(URL))
        viewModel.onResultAction(VerificationResultAction.Back)
        viewModel.onVerifyAction(VerifyAction.Back)

        effects shouldBe listOf(
            VerifyEffect.OpenHistory(2),
            VerifyEffect.OpenUrl(URL),
            VerifyEffect.Close,
            VerifyEffect.Close
        )
    }

    @Test
    fun `aberto por id de registro mostra o resultado sem o Photo Picker`() = runTest(dispatcher) {
        val result = VerificationResult.RegisteredEdited(Records.edited, Records.original, 12.5)
        coEvery { verifyImage.verifyRecord(2) } returns result

        val viewModel = viewModel(recordId = 2)

        viewModel.ui.value shouldBe VerifyFlowUiState(result = VerificationResultUiState(result))
        viewModel.ui.value.result?.thumbnail.shouldBeNull()
    }

    @Test
    fun `aberto por id, tentar de novo consulta o registro outra vez`() = runTest(dispatcher) {
        coEvery { verifyImage.verifyRecord(2) } returns VerificationResult.Error(Reason.NETWORK)
        val viewModel = viewModel(recordId = 2)
        val result = VerificationResult.RegisteredOriginal(Records.original)
        coEvery { verifyImage.verifyRecord(2) } returns result

        viewModel.onResultAction(VerificationResultAction.Retry)

        viewModel.ui.value shouldBe VerifyFlowUiState(result = VerificationResultUiState(result))
    }

    private companion object {
        val BYTES = byteArrayOf(1, 2, 3)
        const val URL = "https://sepolia.etherscan.io/tx/0xabc"
    }
}
