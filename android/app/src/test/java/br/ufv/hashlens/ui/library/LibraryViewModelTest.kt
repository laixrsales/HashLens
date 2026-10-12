package br.ufv.hashlens.ui.library

import android.graphics.Bitmap
import android.net.Uri
import br.ufv.hashlens.R
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.ImageDecoder
import br.ufv.hashlens.data.chain.TxCost
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.domain.usecase.CaptureResult
import br.ufv.hashlens.domain.usecase.RetryRegistrationUseCase
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.work.PendingTxScheduler
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.FileNotFoundException
import java.math.BigInteger
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
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

/**
 * T086, T089 e T090: Meu acervo (ux.md §4.10; US6, FR-029): as fotos deste aparelho agrupadas pela
 * original, com o selo de status de cada uma, o arquivo removido da galeria e o "Tentar de novo" das
 * que falharam, que passa pelo [RetryRegistrationUseCase] (checagem do hash antes de abrir a carteira,
 * R23). Tocar numa foto abre as opções: histórico e edição, só das registradas.
 *
 * Ao abrir, o acervo relê cada arquivo da galeria (T090): apagado ou alterado (SHA-256 diferente do
 * registrado) vira `fileAvailable = false`, e de volta vira `true`. Da mesma leitura sai a miniatura.
 *
 * O acervo (Room) é um fluxo falso que reflete `setFileAvailable`; a galeria, o decodificador, o caso
 * de uso, a carteira e o agendador também. O SHA-256 é o real, sobre os bytes falsos de cada foto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val stored = MutableStateFlow<List<LocalImageEntity>>(emptyList())
    private val images = mockk<LocalImageDao> {
        every { observeAll() } returns stored
        // Como o Room: a mudança volta pelo fluxo
        coEvery { setFileAvailable(any(), any()) } answers {
            val (localId, available) = firstArg<Long>() to secondArg<Boolean>()
            stored.value = stored.value.map { if (it.localId == localId) it.copy(fileAvailable = available) else it }
        }
    }
    private val gallery = mockk<GalleryRepository>()
    private val decoder = mockk<ImageDecoder>()
    private val bitmap = mockk<Bitmap>()

    /** Fotos fora da galeria e fotos alteradas depois do registro, por `localId`. */
    private val removed = mutableSetOf<Long>()
    private val altered = mutableSetOf<Long>()
    private val uris = mutableMapOf<Uri, Long>()
    private val retryRegistration = mockk<RetryRegistrationUseCase>()
    private val walletState = MutableStateFlow<WalletState>(CONNECTED)
    private val session = mockk<WalletSession> { every { state } returns walletState }
    private var tracked = 0
    private val scheduler = PendingTxScheduler { tracked++ }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Teste JVM sem Robolectric: o Uri do Android é só um stub
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val localId = firstArg<String>().substringAfterLast('/').toLong()
            mockk<Uri>(name = firstArg()).also { uris[it] = localId }
        }
        coEvery { gallery.read(any()) } answers {
            val localId = uris.getValue(firstArg())
            if (localId in removed) throw FileNotFoundException("apagada")
            bytes(localId) + if (localId in altered) byteArrayOf(1) else byteArrayOf()
        }
        every { decoder.decodeThumbnail(any(), any()) } returns bitmap
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = LibraryViewModel(
        images = images,
        retryRegistration = retryRegistration,
        session = session,
        scheduler = scheduler,
        gallery = gallery,
        decoder = decoder,
        sha256Hasher = Sha256Hasher(),
        defaultDispatcher = dispatcher,
        applicationScope = backgroundScope
    )

    private fun bytes(localId: Long) = "foto-$localId".encodeToByteArray()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    private fun TestScope.effects(viewModel: LibraryViewModel): List<LibraryEffect> {
        val collected = mutableListOf<LibraryEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { collected += it } }
        return collected
    }

    private fun image(
        localId: Long,
        status: RegistrationStatus,
        origin: ImageOrigin = ImageOrigin.CAPTURE,
        recordId: Long? = null,
        originalRecordId: Long? = recordId.takeIf { origin == ImageOrigin.CAPTURE },
        failureReason: FailureReason? = null,
        fileAvailable: Boolean = true,
        createdAt: Long = localId * 1_000
    ) = LocalImageEntity(
        localId = localId,
        contentUri = "content://media/external/images/media/$localId",
        sha256Hex = sha256(bytes(localId)),
        pHashHex = "fe44819b7ec4c03b",
        origin = origin,
        parentRecordId = originalRecordId.takeIf { origin == ImageOrigin.EDIT },
        originalRecordId = originalRecordId,
        walletAddress = ADDRESS.lowercase(),
        status = status,
        recordId = recordId,
        failureReason = failureReason,
        createdAt = createdAt,
        fileAvailable = fileAvailable
    ).also { if (!fileAvailable) removed += localId }

    private fun LibraryViewModel.groups(): List<List<Long>> = ui.value.groups.map { group ->
        group.items.map { it.localId }
    }

    private fun LibraryViewModel.item(localId: Long): LibraryItem =
        ui.value.groups.flatMap { it.items }.single { it.localId == localId }

    @Test
    fun `acervo vazio`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.ui.value.groups.shouldBeEmpty()
        viewModel.ui.value.loading shouldBe false
    }

    @Test
    fun `agrupa pela original, com a original primeiro e os grupos mais recentes no topo`() = runTest(dispatcher) {
        stored.value = listOf(
            // Captura confirmada (registro 10) e duas versões dela, uma confirmada e uma que falhou
            image(1, RegistrationStatus.CONFIRMED, recordId = 10),
            image(2, RegistrationStatus.CONFIRMED, ImageOrigin.EDIT, recordId = 11, originalRecordId = 10),
            image(4, RegistrationStatus.FAILED, ImageOrigin.EDIT, originalRecordId = 10, createdAt = 9_000),
            // Captura que nunca chegou à rede: grupo próprio
            image(3, RegistrationStatus.FAILED, failureReason = FailureReason.REJECTED),
            // Edição de uma foto de terceiros (original 50 não está neste aparelho)
            image(5, RegistrationStatus.PENDING, ImageOrigin.EDIT, originalRecordId = 50)
        )

        val viewModel = viewModel()

        // Ordem dos grupos pela foto mais recente de cada um: 4 (9 s), 5 (5 s), 3 (3 s)
        viewModel.groups() shouldContainExactly listOf(listOf(1L, 2L, 4L), listOf(5L), listOf(3L))
    }

    @Test
    fun `cada foto tem o selo do vocabulário de status`() = runTest(dispatcher) {
        stored.value = listOf(
            image(1, RegistrationStatus.CONFIRMED, recordId = 10),
            image(2, RegistrationStatus.CONFIRMED, ImageOrigin.EDIT, recordId = 11, originalRecordId = 10),
            image(3, RegistrationStatus.PENDING),
            image(4, RegistrationStatus.AWAITING_WALLET),
            image(5, RegistrationStatus.FAILED, failureReason = FailureReason.REJECTED)
        )

        val viewModel = viewModel()

        viewModel.item(1).status shouldBe ImageStatus.Original
        viewModel.item(2).status shouldBe ImageStatus.Edited
        viewModel.item(3).status shouldBe ImageStatus.Pending
        viewModel.item(4).status shouldBe ImageStatus.Pending
        viewModel.item(5).status shouldBe ImageStatus.Failed
    }

    @Test
    fun `FAILED(TIMEOUT) aparece como Não registrada – tentar de novo`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT))

        val item = viewModel().item(1)

        item.status shouldBe ImageStatus.Failed
        item.status.title shouldBe R.string.status_failed
        item.canRetry shouldBe true
    }

    @Test
    fun `só as que falharam oferecem Tentar de novo`() = runTest(dispatcher) {
        stored.value = listOf(
            image(1, RegistrationStatus.CONFIRMED, recordId = 10),
            image(2, RegistrationStatus.PENDING),
            image(3, RegistrationStatus.FAILED, failureReason = FailureReason.NETWORK)
        )

        val viewModel = viewModel()

        listOf(1L, 2L, 3L).map { viewModel.item(it).canRetry } shouldContainExactly listOf(false, false, true)
    }

    @Test
    fun `arquivo removido da galeria fica sinalizado e continua no acervo`() = runTest(dispatcher) {
        stored.value = listOf(
            image(1, RegistrationStatus.CONFIRMED, recordId = 10, fileAvailable = false),
            image(2, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT, fileAvailable = false)
        )

        val viewModel = viewModel()

        viewModel.item(1).fileAvailable shouldBe false
        viewModel.item(1).status shouldBe ImageStatus.Original
        // O reenvio usa os hashes e a assinatura guardados, não o arquivo
        viewModel.item(2).canRetry shouldBe true
    }

    @Test
    fun `acompanha o acervo, e o reenvio confirmado muda o selo sem recarregar`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT))
        val viewModel = viewModel()

        stored.value = listOf(image(1, RegistrationStatus.CONFIRMED, recordId = 10))

        viewModel.item(1).status shouldBe ImageStatus.Original
    }

    @Test
    fun `filtros de pendentes e com falha`() = runTest(dispatcher) {
        stored.value = listOf(
            image(1, RegistrationStatus.CONFIRMED, recordId = 10),
            image(2, RegistrationStatus.PENDING, ImageOrigin.EDIT, originalRecordId = 10),
            image(3, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT)
        )
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.SetFilter(LibraryFilter.Pending))
        viewModel.groups() shouldContainExactly listOf(listOf(2L))

        viewModel.onAction(LibraryAction.SetFilter(LibraryFilter.Failed))
        viewModel.groups() shouldContainExactly listOf(listOf(3L))

        viewModel.onAction(LibraryAction.SetFilter(LibraryFilter.All))
        viewModel.groups() shouldContainExactly listOf(listOf(3L), listOf(1L, 2L))
    }

    @Test
    fun `Tentar de novo reenvia pelo RetryRegistrationUseCase com a carteira conectada`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT))
        coEvery { retryRegistration(1, ADDRESS, any()) } returns CaptureResult.Submitted(1, TX)
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.Retry(1))

        coVerify(exactly = 1) { retryRegistration(1, ADDRESS, any()) }
        // Enviada: o acompanhamento em segundo plano assume
        tracked shouldBe 1
    }

    @Test
    fun `reenvio confirmado pelo hash não agenda acompanhamento`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT))
        coEvery { retryRegistration(1, ADDRESS, any()) } returns CaptureResult.Confirmed(1, recordId = 10)
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.Retry(1))

        tracked shouldBe 0
    }

    @Test
    fun `durante o reenvio a foto não oferece Tentar de novo outra vez`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT))
        val answer = CompletableDeferred<CaptureResult>()
        coEvery { retryRegistration(1, ADDRESS, any()) } coAnswers { answer.await() }
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.Retry(1))
        viewModel.item(1).canRetry shouldBe false
        viewModel.onAction(LibraryAction.Retry(1))

        answer.complete(CaptureResult.Failed(1, FailureReason.REJECTED))
        viewModel.item(1).canRetry shouldBe true
        coVerify(exactly = 1) { retryRegistration(any(), any(), any()) }
    }

    @Test
    fun `com outra carteira conectada avisa qual carteira conectar`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT))
        walletState.value = WalletState.Connected(OTHER, ChainConfig.SEPOLIA_CHAIN_ID)
        coEvery { retryRegistration(1, OTHER, any()) } returns CaptureResult.WrongWallet(1, ADDRESS.lowercase())
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(LibraryAction.Retry(1))

        // "Conecte a carteira 0x1234…5678 usada neste registro para tentar de novo."
        effects shouldContainExactly listOf(
            LibraryEffect.Notice(R.string.notice_wrong_wallet_retry, AddressFormat.short(ADDRESS.lowercase()))
        )
    }

    @Test
    fun `sem carteira na Sepolia avisa e não tenta`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT))
        val viewModel = viewModel()
        val effects = effects(viewModel)

        walletState.value = WalletState.Disconnected
        viewModel.onAction(LibraryAction.Retry(1))
        walletState.value = WalletState.Connected(ADDRESS, chainId = 1)
        viewModel.onAction(LibraryAction.Retry(1))

        effects shouldContainExactly List(2) { LibraryEffect.Notice(R.string.notice_wallet_not_ready) }
        coVerify(exactly = 0) { retryRegistration(any(), any(), any()) }
    }

    @Test
    fun `imagem presa em LOCAL_ONLY aparece como falha e oferece Tentar de novo`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.LOCAL_ONLY))

        val item = viewModel().item(1)

        item.status shouldBe ImageStatus.Failed
        item.canRetry shouldBe true
    }

    @Test
    fun `saldo insuficiente ou rede fora no reenvio viram aviso`() = runTest(dispatcher) {
        stored.value = listOf(
            image(1, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT),
            image(2, RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT)
        )
        val cost = TxCost(gasLimit = BigInteger.ONE, gasPrice = BigInteger.ONE, balance = BigInteger.ZERO)
        coEvery { retryRegistration(1, ADDRESS, any()) } returns CaptureResult.InsufficientFunds(1, cost)
        coEvery { retryRegistration(2, ADDRESS, any()) } returns CaptureResult.Failed(2, FailureReason.NETWORK)
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(LibraryAction.Retry(1))
        viewModel.onAction(LibraryAction.Retry(2))

        effects shouldContainExactly listOf(
            LibraryEffect.Notice(R.string.notice_insufficient_funds_retry),
            LibraryEffect.Notice(R.string.notice_network_retry)
        )
    }

    @Test
    fun `tocar numa foto abre as opções e o histórico leva ao registro dela`() = runTest(dispatcher) {
        stored.value =
            listOf(image(2, RegistrationStatus.CONFIRMED, ImageOrigin.EDIT, recordId = 11, originalRecordId = 10))
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(LibraryAction.Select(2))
        viewModel.ui.value.selectedItem.shouldNotBeNull().localId shouldBe 2
        viewModel.onAction(LibraryAction.OpenHistory(11))

        // A folha fecha e a navegação vai ao Histórico do registro 11 (Route.genealogy)
        viewModel.ui.value.selectedItem.shouldBeNull()
        effects shouldContainExactly listOf(LibraryEffect.OpenHistory(11))
    }

    @Test
    fun `histórico e edição só para fotos registradas, e edição só com o arquivo na galeria`() = runTest(dispatcher) {
        stored.value = listOf(
            image(1, RegistrationStatus.CONFIRMED, recordId = 10),
            image(
                2,
                RegistrationStatus.CONFIRMED,
                ImageOrigin.EDIT,
                recordId = 11,
                originalRecordId = 10,
                fileAvailable = false
            ),
            image(3, RegistrationStatus.PENDING),
            image(4, RegistrationStatus.FAILED, failureReason = FailureReason.REJECTED)
        )

        val viewModel = viewModel()

        listOf(1L, 2L, 3L, 4L).map { viewModel.item(it).canOpenHistory } shouldContainExactly
            listOf(true, true, false, false)
        listOf(1L, 2L, 3L, 4L).map { viewModel.item(it).canEdit } shouldContainExactly
            listOf(true, false, false, false)
        viewModel.item(1).recordId shouldBe 10
    }

    @Test
    fun `editar abre o Editor com a foto do acervo`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.CONFIRMED, recordId = 10))
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(LibraryAction.Select(1))
        viewModel.onAction(LibraryAction.Edit(1))

        viewModel.ui.value.selectedItem.shouldBeNull()
        effects shouldContainExactly listOf(LibraryEffect.Edit(1))
    }

    @Test
    fun `voltar e Registrar foto do estado vazio viram efeitos`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(LibraryAction.RegisterPhoto)
        viewModel.onAction(LibraryAction.Back)

        effects shouldContainExactly listOf(LibraryEffect.RegisterPhoto, LibraryEffect.Close)
    }

    @Test
    fun `ao abrir, foto apagada da galeria passa a indisponível`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.CONFIRMED, recordId = 10))
        removed += 1

        val viewModel = viewModel()

        coVerify(exactly = 1) { images.setFileAvailable(1, false) }
        viewModel.item(1).fileAvailable shouldBe false
        viewModel.item(1).thumbnail.shouldBeNull()
    }

    @Test
    fun `ao abrir, foto alterada depois do registro também passa a indisponível`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.CONFIRMED, recordId = 10))
        altered += 1

        val viewModel = viewModel()

        coVerify(exactly = 1) { images.setFileAvailable(1, false) }
        viewModel.item(1).fileAvailable shouldBe false
    }

    @Test
    fun `foto que voltou para a galeria passa a disponível de novo`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.CONFIRMED, recordId = 10, fileAvailable = false))
        // Devolvida igual (ex.: restaurada da lixeira)
        removed -= 1

        val viewModel = viewModel()

        coVerify(exactly = 1) { images.setFileAvailable(1, true) }
        viewModel.item(1).fileAvailable shouldBe true
    }

    @Test
    fun `foto intacta não muda o acervo e ganha a miniatura`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.CONFIRMED, recordId = 10))

        val viewModel = viewModel()

        coVerify(exactly = 0) { images.setFileAvailable(any(), any()) }
        viewModel.item(1).thumbnail.shouldNotBeNull()
    }

    @Test
    fun `foto que não decodifica continua disponível, só sem miniatura`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.CONFIRMED, recordId = 10))
        every { decoder.decodeThumbnail(any(), any()) } throws IllegalArgumentException("formato")

        val viewModel = viewModel()

        coVerify(exactly = 0) { images.setFileAvailable(any(), any()) }
        viewModel.item(1).fileAvailable shouldBe true
        viewModel.item(1).thumbnail.shouldBeNull()
    }

    @Test
    fun `fotos que chegam com o acervo aberto também são conferidas uma vez`() = runTest(dispatcher) {
        stored.value = listOf(image(1, RegistrationStatus.CONFIRMED, recordId = 10))
        val viewModel = viewModel()

        stored.value = stored.value + image(2, RegistrationStatus.PENDING)
        // Mudança de status da mesma foto não relê o arquivo
        stored.value = stored.value.map { if (it.localId == 2L) it.copy(status = RegistrationStatus.CONFIRMED) else it }

        coVerify(exactly = 1) { gallery.read(match { uris[it] == 1L }) }
        coVerify(exactly = 1) { gallery.read(match { uris[it] == 2L }) }
        viewModel.item(2).thumbnail.shouldNotBeNull()
    }

    private companion object {
        const val ADDRESS = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val OTHER = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01"
        const val TX = "0x3333333333333333333333333333333333333333333333333333333333333333"
        val CONNECTED = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)
    }
}
