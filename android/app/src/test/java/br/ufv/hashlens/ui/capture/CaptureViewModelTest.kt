package br.ufv.hashlens.ui.capture

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import br.ufv.hashlens.R
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.TxCost
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletEvent
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.domain.usecase.CaptureAndRegisterUseCase
import br.ufv.hashlens.domain.usecase.CaptureResult
import br.ufv.hashlens.domain.usecase.RegistrationProgress
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.work.PendingTxReconciler
import br.ufv.hashlens.work.PendingTxScheduler
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T057 e T058: câmera → Acompanhamento do registro (ux.md §4.4 e §4.5) a partir do progresso e do
 * resultado do caso de uso, do acompanhamento da transação e da carteira durante a operação.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val walletState = MutableStateFlow<WalletState>(CONNECTED)
    private val session = mockk<WalletSession> {
        every { state } returns walletState
        every { events } returns MutableSharedFlow<WalletEvent>()
    }
    private val captureAndRegister = mockk<CaptureAndRegisterUseCase>()
    private val reconciler = mockk<PendingTxReconciler>(relaxUnitFun = true)
    private val images = mockk<LocalImageDao>()
    private val reader = mockk<RegistryReader>()
    private var tracked = 0
    private val scheduler = PendingTxScheduler { tracked++ }
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val photo = mockk<Bitmap>()
    private val thumbnail = mockk<ImageBitmap>()

    @BeforeEach
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = CaptureViewModel(
        captureAndRegister = captureAndRegister,
        reconciler = reconciler,
        images = images,
        reader = reader,
        session = session,
        scheduler = scheduler,
        config = config,
        clock = clock,
        applicationScope = backgroundScope
    )

    private fun TestScope.effects(viewModel: CaptureViewModel): List<CaptureEffect> {
        val collected = mutableListOf<CaptureEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { collected += it } }
        return collected
    }

    /** O caso de uso avisa [progress] e termina com [result]. */
    private fun capture(result: CaptureResult, vararg progress: RegistrationProgress) {
        coEvery { captureAndRegister(photo, ROTATION, ADDRESS, any()) } coAnswers {
            val onProgress = arg<(RegistrationProgress) -> Unit>(3)
            progress.forEach(onProgress)
            result
        }
    }

    private fun image(status: RegistrationStatus, recordId: Long? = null, failure: FailureReason? = null) =
        LocalImageEntity(
            localId = LOCAL_ID,
            contentUri = "content://media/external/images/media/42",
            sha256Hex = SHA.hex,
            pHashHex = "fe44819b7ec4c03b",
            origin = ImageOrigin.CAPTURE,
            walletAddress = ADDRESS.lowercase(),
            deviceId = 3,
            status = status,
            txHash = TX,
            recordId = recordId,
            failureReason = failure,
            createdAt = NOW.toEpochMilli()
        )

    private fun CaptureViewModel.stage() = ui.value.progress.shouldNotBeNull().stage

    @Test
    fun `sem carteira pronta não fotografa e avisa`() = runTest(dispatcher) {
        walletState.value = WalletState.Connected(ADDRESS, chainId = 1)
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.canTakePhoto() shouldBe false

        effects shouldBe listOf(CaptureEffect.Notice(R.string.notice_wallet_not_ready))
    }

    @Test
    fun `com a carteira aberta mostra a aprovação com o custo estimado`() = runTest(dispatcher) {
        coEvery { captureAndRegister(photo, ROTATION, ADDRESS, any()) } coAnswers {
            val onProgress = arg<(RegistrationProgress) -> Unit>(3)
            onProgress(RegistrationProgress.Saved(LOCAL_ID))
            onProgress(RegistrationProgress.AwaitingWallet(COST))
            kotlinx.coroutines.awaitCancellation()
        }
        val viewModel = viewModel()

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)

        viewModel.stage() shouldBe RegistrationStage.AwaitingApproval(BigDecimal("0.0002"))
        viewModel.ui.value.progress?.thumbnail shouldBe thumbnail
    }

    @Test
    fun `enviado fica pendente e agenda o acompanhamento em segundo plano`() = runTest(dispatcher) {
        capture(CaptureResult.Submitted(LOCAL_ID, TX), RegistrationProgress.Saved(LOCAL_ID))
        coEvery { images.getById(LOCAL_ID) } returns image(RegistrationStatus.PENDING)
        val viewModel = viewModel()

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)

        viewModel.stage() shouldBe RegistrationStage.Pending
        tracked shouldBe 1
    }

    @Test
    fun `vínculo do aparelho aparece antes da aprovação`() = runTest(dispatcher) {
        coEvery { captureAndRegister(photo, ROTATION, ADDRESS, any()) } coAnswers {
            val onProgress = arg<(RegistrationProgress) -> Unit>(3)
            onProgress(RegistrationProgress.Saved(LOCAL_ID))
            onProgress(RegistrationProgress.BindingDevice(newKey = true, cost = COST))
            kotlinx.coroutines.awaitCancellation()
        }
        val viewModel = viewModel()

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)

        viewModel.stage() shouldBe RegistrationStage.BindingDevice(newKey = true)
    }

    @Test
    fun `pendente confirma pelo acompanhamento e mostra a data e os detalhes técnicos`() = runTest(dispatcher) {
        capture(CaptureResult.Submitted(LOCAL_ID, TX), RegistrationProgress.Saved(LOCAL_ID))
        coEvery { images.getById(LOCAL_ID) } returnsMany listOf(
            image(RegistrationStatus.PENDING),
            image(RegistrationStatus.CONFIRMED, recordId = 7)
        )
        coEvery { reader.getRecord(7) } returns record(7)
        val viewModel = viewModel()

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)
        advanceTimeBy(CaptureViewModel.POLL_INTERVAL_MS + 1)

        viewModel.stage() shouldBe RegistrationStage.Confirmed(REGISTERED_AT)
        val technical = viewModel.ui.value.progress.shouldNotBeNull().technical
        technical.first().value shouldBe "#7"
        technical.last().url shouldBe config.transactionUrl(TX)
        coVerify { reconciler.check(any()) }
    }

    @Test
    fun `pendente que falha por prazo mostra a falha`() = runTest(dispatcher) {
        capture(CaptureResult.Submitted(LOCAL_ID, TX))
        coEvery { images.getById(LOCAL_ID) } returns
            image(RegistrationStatus.FAILED, failure = FailureReason.TIMEOUT)
        val viewModel = viewModel()

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)

        viewModel.stage() shouldBe RegistrationStage.Failed(RegistrationFailure.Timeout)
    }

    @Test
    fun `saldo insuficiente mostra custo e saldo e leva ao faucet`() = runTest(dispatcher) {
        capture(CaptureResult.InsufficientFunds(LOCAL_ID, LOW))
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)
        viewModel.onProgressAction(RegistrationProgressAction.OpenFaucet)

        viewModel.stage() shouldBe RegistrationStage.InsufficientFunds(BigDecimal("0.0002"), BigDecimal("0.00001"))
        effects shouldBe listOf(CaptureEffect.OpenUrl(config.faucetUrl))
    }

    @Test
    fun `recusa na carteira e tentar de novo reenvia com a mesma carteira`() = runTest(dispatcher) {
        capture(CaptureResult.Failed(LOCAL_ID, FailureReason.REJECTED))
        coEvery { captureAndRegister.resubmit(LOCAL_ID, ADDRESS, any()) } returns
            CaptureResult.Submitted(LOCAL_ID, TX)
        coEvery { images.getById(LOCAL_ID) } returns image(RegistrationStatus.PENDING)
        val viewModel = viewModel()

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)
        viewModel.stage() shouldBe RegistrationStage.Failed(RegistrationFailure.Rejected)
        viewModel.onProgressAction(RegistrationProgressAction.Retry)

        viewModel.stage() shouldBe RegistrationStage.Pending
        coVerify { captureAndRegister.resubmit(LOCAL_ID, ADDRESS, any()) }
    }

    @Test
    fun `tentar de novo com outra conta avisa qual carteira conectar`() = runTest(dispatcher) {
        capture(CaptureResult.Failed(LOCAL_ID, FailureReason.REJECTED))
        coEvery { captureAndRegister.resubmit(LOCAL_ID, OTHER, any()) } returns
            CaptureResult.WrongWallet(LOCAL_ID, ADDRESS.lowercase())
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)
        walletState.value = WalletState.Connected(OTHER, ChainConfig.SEPOLIA_CHAIN_ID)

        viewModel.onProgressAction(RegistrationProgressAction.Retry)

        viewModel.stage() shouldBe RegistrationStage.Failed(RegistrationFailure.Rejected)
        effects shouldBe listOf(
            CaptureEffect.Notice(R.string.notice_wrong_wallet_retry, AddressFormat.short(ADDRESS.lowercase()))
        )
    }

    @Test
    fun `já registrada leva ao registro no explorador enquanto a verificação não existe`() = runTest(dispatcher) {
        capture(CaptureResult.AlreadyRegistered(recordId = 5))
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)
        viewModel.onProgressAction(RegistrationProgressAction.ViewRecord(5))

        viewModel.stage() shouldBe RegistrationStage.AlreadyRegistered(5)
        effects shouldBe listOf(CaptureEffect.OpenUrl(config.addressUrl(CONTRACT)))
    }

    @Test
    fun `troca de conta ou de rede com a carteira aberta avisa`() = runTest(dispatcher) {
        coEvery { captureAndRegister(photo, ROTATION, ADDRESS, any()) } coAnswers {
            arg<(RegistrationProgress) -> Unit>(3)(RegistrationProgress.AwaitingWallet(COST))
            kotlinx.coroutines.awaitCancellation()
        }
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)

        walletState.value = WalletState.Connected(OTHER, ChainConfig.SEPOLIA_CHAIN_ID)
        walletState.value = WalletState.Connected(ADDRESS, chainId = 1)
        walletState.value = WalletState.Disconnected

        effects shouldBe listOf(
            CaptureEffect.Notice(R.string.notice_wallet_account_changed, AddressFormat.short(ADDRESS)),
            CaptureEffect.Notice(R.string.notice_wallet_network_changed),
            CaptureEffect.Notice(R.string.notice_wallet_disconnected)
        )
    }

    @Test
    fun `falha ao salvar na galeria pede para fotografar de novo`() = runTest(dispatcher) {
        coEvery { captureAndRegister(photo, ROTATION, ADDRESS, any()) } throws IOException("sem espaço")
        val viewModel = viewModel()

        viewModel.onPhotoTaken(photo, ROTATION, thumbnail)

        viewModel.stage() shouldBe RegistrationStage.Failed(RegistrationFailure.SaveFailed)
        viewModel.onProgressAction(RegistrationProgressAction.RetakePhoto)
        viewModel.ui.value.progress.shouldBeNull()
        viewModel.ui.value.camera.camera shouldBe CameraState.Ready
    }

    @Test
    fun `flash alterna e a câmera troca de lente`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onCameraAction(CaptureAction.ToggleFlash)
        viewModel.ui.value.camera.flash shouldBe FlashMode.On
        viewModel.onCameraAction(CaptureAction.ToggleFlash)
        viewModel.ui.value.camera.flash shouldBe FlashMode.Auto
        viewModel.onCameraAction(CaptureAction.ToggleFlash)
        viewModel.ui.value.camera.flash shouldBe FlashMode.Off

        viewModel.onCameraAction(CaptureAction.SwitchCamera)
        viewModel.ui.value.lens shouldBe CameraLens.Front
    }

    @Test
    fun `erro da câmera e tentar de novo`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onCameraError()
        viewModel.ui.value.camera.camera shouldBe CameraState.Error
        viewModel.onCameraAction(CaptureAction.Retry)
        viewModel.ui.value.camera.camera.shouldBeInstanceOf<CameraState.Ready>()
    }

    private fun record(id: Long) = ImageRecord(
        id = id,
        sha256 = SHA,
        pHash = PHash.fromHex("fe44819b7ec4c03b"),
        parentId = null,
        originalId = id,
        registrant = ADDRESS,
        deviceId = 3,
        signature = ByteArray(64),
        timestamp = REGISTERED_AT,
        operations = ""
    )

    private companion object {
        const val CONTRACT = "0xF2f42B34414936e456c6AC10c4844f456CAD65Ba"
        const val ADDRESS = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val OTHER = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01"
        const val TX = "0x3333333333333333333333333333333333333333333333333333333333333333"
        const val LOCAL_ID = 11L
        const val ROTATION = 90
        val CONNECTED = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)
        val NOW: Instant = Instant.parse("2026-10-10T18:00:00Z")
        val REGISTERED_AT: Instant = Instant.parse("2026-10-10T18:00:24Z")
        val SHA = Sha256("55824746f88c258e4ef10a10027ba7751e85b67c866f63023bdf90c2207aa527")

        // 200 mil de gás × 1 gwei = 0,0002 SepoliaETH
        val COST = TxCost(BigInteger.valueOf(200_000), BigInteger.valueOf(1_000_000_000), BigInteger.TEN.pow(18))
        val LOW = COST.copy(balance = BigInteger.TEN.pow(13))
    }
}
