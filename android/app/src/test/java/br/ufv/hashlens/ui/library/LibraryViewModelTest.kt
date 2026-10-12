package br.ufv.hashlens.ui.library

import br.ufv.hashlens.R
import br.ufv.hashlens.config.ChainConfig
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
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
 * T086: Meu acervo (ux.md §4.10; US6, FR-029): as fotos deste aparelho agrupadas pela original, com o
 * selo de status de cada uma, o arquivo removido da galeria e o "Tentar de novo" das que falharam,
 * que passa pelo [RetryRegistrationUseCase] (checagem do hash antes de abrir a carteira, R23).
 *
 * O acervo (Room) é um fluxo falso; o caso de uso, a carteira e o agendador também.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val stored = MutableStateFlow<List<LocalImageEntity>>(emptyList())
    private val images = mockk<LocalImageDao> { every { observeAll() } returns stored }
    private val retryRegistration = mockk<RetryRegistrationUseCase>()
    private val walletState = MutableStateFlow<WalletState>(CONNECTED)
    private val session = mockk<WalletSession> { every { state } returns walletState }
    private var tracked = 0
    private val scheduler = PendingTxScheduler { tracked++ }

    @BeforeEach
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = LibraryViewModel(
        images = images,
        retryRegistration = retryRegistration,
        session = session,
        scheduler = scheduler,
        applicationScope = backgroundScope
    )

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
        sha256Hex = "%064x".format(localId),
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
    )

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

    private companion object {
        const val ADDRESS = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val OTHER = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01"
        const val TX = "0x3333333333333333333333333333333333333333333333333333333333333333"
        val CONNECTED = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)
    }
}
