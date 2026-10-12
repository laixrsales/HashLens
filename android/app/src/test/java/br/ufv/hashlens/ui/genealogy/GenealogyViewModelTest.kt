package br.ufv.hashlens.ui.genealogy

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.ImageDecoder
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.domain.model.GenealogyNode
import br.ufv.hashlens.domain.model.RecordView
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.domain.usecase.GenealogyResult
import br.ufv.hashlens.domain.usecase.TraceGenealogyUseCase
import br.ufv.hashlens.ui.capture.toThumbnail
import br.ufv.hashlens.ui.preview.SampleData.Records
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.FileNotFoundException
import kotlinx.coroutines.CompletableDeferred
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
 * T083: Histórico da imagem ligado ao caso de uso (ux.md §4.9; US5). O caso de uso, o acervo, a
 * galeria, o hash e o decodificador são falsos.
 *
 * A miniatura só aparece para versões deste aparelho cujo arquivo ainda é o registrado (o SHA-256
 * confere); arquivo apagado, alterado ou que não decodifica fica sem miniatura, sem erro na tela.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenealogyViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val traceGenealogy = mockk<TraceGenealogyUseCase>()
    private val images = mockk<LocalImageDao>()
    private val gallery = mockk<GalleryRepository>()
    private val decoder = mockk<ImageDecoder>()
    private val sha256Hasher = mockk<Sha256Hasher>()
    private val bitmap = mockk<Bitmap>()
    private val thumbnail = mockk<ImageBitmap>()
    private val uris = mutableMapOf<String, Uri>()

    // Original → editada (consultada); a original tem mais uma versão fora do caminho
    private val edited = node(Records.edited)
    private val other = node(Records.editedZero)
    private val genealogy = Genealogy(
        root = node(Records.original, listOf(edited, other)),
        pathToQueried = listOf(1, 2),
        truncated = false
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Uri::class)
        mockkStatic(CAMERA_CONTROLLER)
        // Teste JVM sem Robolectric: o Uri do Android é só um stub
        every { Uri.parse(any()) } answers { uri(firstArg()) }
        every { bitmap.toThumbnail(any(), any()) } returns thumbnail
        coEvery { images.getConfirmedByRecordIds(any()) } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(CAMERA_CONTROLLER)
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun uri(value: String): Uri = uris.getOrPut(value) { mockk(name = value) }

    private fun node(record: RecordView, children: List<GenealogyNode> = emptyList()) =
        GenealogyNode(record, percentFromParent = null, percentFromOriginal = 0.0, signatureValid = true, children)

    private fun viewModel(recordId: Long = 2) = GenealogyViewModel(
        traceGenealogy = traceGenealogy,
        images = images,
        gallery = gallery,
        decoder = decoder,
        sha256Hasher = sha256Hasher,
        defaultDispatcher = dispatcher,
        savedStateHandle = SavedStateHandle(mapOf(GenealogyViewModel.RECORD_ID to recordId))
    )

    private fun TestScope.effects(viewModel: GenealogyViewModel): List<GenealogyEffect> {
        val collected = mutableListOf<GenealogyEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { collected += it } }
        return collected
    }

    private fun local(recordId: Long, sha256Hex: String, fileAvailable: Boolean = true) = LocalImageEntity(
        contentUri = "content://media/$recordId",
        sha256Hex = sha256Hex,
        pHashHex = Records.original.pHash,
        origin = ImageOrigin.EDIT,
        walletAddress = "0xabc",
        status = RegistrationStatus.CONFIRMED,
        recordId = recordId,
        createdAt = 0,
        fileAvailable = fileAvailable
    )

    /** A galeria devolve [bytes] para o arquivo do registro [recordId], com o SHA-256 [sha256Hex]. */
    private fun file(recordId: Long, bytes: ByteArray, sha256Hex: String) {
        coEvery { gallery.read(uri("content://media/$recordId")) } returns bytes
        every { sha256Hasher.hash(any()) } returns Sha256(sha256Hex)
        every { decoder.decode(bytes) } returns bitmap
    }

    @Test
    fun `carrega a genealogia do registro do argumento`() = runTest(dispatcher) {
        coEvery { traceGenealogy(2) } returns GenealogyResult.Traced(genealogy)

        viewModel().ui.value shouldBe GenealogyUiState.Loaded(genealogy)
    }

    @Test
    fun `mostra Carregando enquanto o caso de uso não responde`() = runTest(dispatcher) {
        val answer = CompletableDeferred<GenealogyResult>()
        coEvery { traceGenealogy(2) } coAnswers { answer.await() }

        val viewModel = viewModel()
        viewModel.ui.value shouldBe GenealogyUiState.Loading

        answer.complete(GenealogyResult.Traced(genealogy))
        viewModel.ui.value shouldBe GenealogyUiState.Loaded(genealogy)
    }

    @Test
    fun `sem conexão mostra o erro e Tentar de novo consulta outra vez`() = runTest(dispatcher) {
        coEvery { traceGenealogy(2) } returns GenealogyResult.Error(Reason.NETWORK)
        val viewModel = viewModel()
        viewModel.ui.value shouldBe GenealogyUiState.Error(Reason.NETWORK)

        coEvery { traceGenealogy(2) } returns GenealogyResult.Traced(genealogy)
        viewModel.onAction(GenealogyAction.Retry)

        viewModel.ui.value shouldBe GenealogyUiState.Loaded(genealogy)
        coVerify(exactly = 2) { traceGenealogy(2) }
    }

    @Test
    fun `abrir e recolher as outras versões de um nó`() = runTest(dispatcher) {
        coEvery { traceGenealogy(2) } returns GenealogyResult.Traced(genealogy)
        val viewModel = viewModel()

        viewModel.onAction(GenealogyAction.ToggleBranches(1))
        viewModel.ui.value.shouldBeInstanceOf<GenealogyUiState.Loaded>().expanded shouldBe setOf(1L)

        viewModel.onAction(GenealogyAction.ToggleBranches(1))
        viewModel.ui.value.shouldBeInstanceOf<GenealogyUiState.Loaded>().expanded shouldBe emptySet()
    }

    @Test
    fun `voltar, abrir outra versão e abrir a transação viram efeitos`() = runTest(dispatcher) {
        coEvery { traceGenealogy(2) } returns GenealogyResult.Traced(genealogy)
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(GenealogyAction.Back)
        viewModel.onAction(GenealogyAction.OpenRecord(3))
        viewModel.onAction(GenealogyAction.OpenTransaction(URL))

        effects shouldBe listOf(
            GenealogyEffect.Close,
            GenealogyEffect.OpenRecord(3),
            GenealogyEffect.OpenUrl(URL)
        )
    }

    @Test
    fun `mostra a miniatura das versões da linha do tempo que estão neste aparelho`() = runTest(dispatcher) {
        coEvery { traceGenealogy(2) } returns GenealogyResult.Traced(genealogy)
        coEvery { images.getConfirmedByRecordIds(listOf(1, 2)) } returns listOf(local(2, Records.edited.sha256))
        file(2, BYTES, Records.edited.sha256)

        viewModel().ui.value.shouldBeInstanceOf<GenealogyUiState.Loaded>().thumbnails shouldBe mapOf(2L to thumbnail)
    }

    @Test
    fun `arquivo apagado, alterado ou que não decodifica fica sem miniatura`() = runTest(dispatcher) {
        coEvery { traceGenealogy(2) } returns GenealogyResult.Traced(genealogy)

        // Alterado desde o registro: o SHA-256 não confere
        coEvery { images.getConfirmedByRecordIds(any()) } returns listOf(local(2, Records.edited.sha256))
        file(2, BYTES, "0".repeat(64))
        viewModel().ui.value.shouldBeInstanceOf<GenealogyUiState.Loaded>().thumbnails.shouldBeEmpty()

        // Apagado da galeria
        coEvery { gallery.read(any()) } throws FileNotFoundException()
        viewModel().ui.value.shouldBeInstanceOf<GenealogyUiState.Loaded>().thumbnails.shouldBeEmpty()

        // Marcado como indisponível no acervo: nem tenta ler
        coEvery { images.getConfirmedByRecordIds(any()) } returns
            listOf(local(2, Records.edited.sha256, fileAvailable = false))
        file(2, BYTES, Records.edited.sha256)
        viewModel().ui.value.shouldBeInstanceOf<GenealogyUiState.Loaded>().thumbnails.shouldBeEmpty()

        // Não decodifica
        coEvery { images.getConfirmedByRecordIds(any()) } returns listOf(local(2, Records.edited.sha256))
        every { decoder.decode(BYTES) } throws IllegalArgumentException("formato não suportado")
        viewModel().ui.value.shouldBeInstanceOf<GenealogyUiState.Loaded>().thumbnails.shouldBeEmpty()
    }

    private companion object {
        val BYTES = byteArrayOf(1, 2, 3)
        const val URL = "https://sepolia.etherscan.io/tx/0xabc"

        /** Arquivo de `Bitmap.toThumbnail`, que usa o Bitmap real. */
        const val CAMERA_CONTROLLER = "br.ufv.hashlens.ui.capture.CameraControllerKt"
    }
}
