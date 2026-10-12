package br.ufv.hashlens.domain.usecase

import android.app.Application
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.chain.TxCost
import br.ufv.hashlens.data.local.AppDatabase
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.testing.SignatureVectors
import br.ufv.hashlens.testing.inMemoryDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T085: "Tentar de novo" de um registro que falhou, pelo acervo, pela captura e pela edição
 * (FR-014, research R23, data-model §2.3).
 *
 * Antes de qualquer transação, consulta `getIdBySha256`: o hash já na rede confirma a linha local sem
 * abrir a carteira (a transação "perdida" foi confirmada depois do prazo); senão reabre a carteira com
 * a mesma assinatura do aparelho. A assinatura cobre o `registrant`, por isso o reenvio exige a
 * carteira do registro: com outra conectada, é bloqueado sem abrir a carteira e sem assinar de novo.
 *
 * Usa o [RegisterLocalImageUseCase] real sobre um Room em memória; contrato, vínculo do aparelho,
 * Keystore e carteira são falsos. As linhas começam em `FAILED`, gravadas direto no acervo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RetryRegistrationUseCaseTest {
    private val db: AppDatabase = inMemoryDatabase()
    private val images = db.localImageDao()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")

    private val reader = mockk<RegistryReader>()
    private val ensureDeviceBound = mockk<EnsureDeviceBoundUseCase>()
    private val keyManager = mockk<DeviceKeyManager>()
    private val wallet = mockk<WalletSession>()
    private val txBuilder = mockk<RegistryTxBuilder>()

    private val useCase = RetryRegistrationUseCase(
        reader = reader,
        ensureDeviceBound = ensureDeviceBound,
        registerLocalImage = RegisterLocalImageUseCase(images, keyManager, wallet, txBuilder, config, clock),
        images = images
    )

    init {
        coEvery { reader.getIdBySha256(SHA) } returns null
        coEvery { ensureDeviceBound(any(), any()) } returns BoundDevice(deviceId = DEVICE_ID, newlyBound = false)
        every { keyManager.sign(any(), any()) } returns SIGNATURE
        coEvery { txBuilder.estimate(any(), any()) } returns ENOUGH
        coEvery { wallet.sendTransaction(any(), any()) } returns TX
    }

    @After
    fun closeDatabase() = db.close()

    /** Registro que falhou depois de assinado: a assinatura e o `deviceId` ficaram guardados. */
    private suspend fun failed(
        reason: FailureReason = FailureReason.TIMEOUT,
        origin: ImageOrigin = ImageOrigin.CAPTURE,
        status: RegistrationStatus = RegistrationStatus.FAILED
    ): Long = images.insert(
        LocalImageEntity(
            contentUri = "content://media/external/images/media/42",
            sha256Hex = SHA.hex,
            pHashHex = PHASH.hex,
            origin = origin,
            parentRecordId = if (origin == ImageOrigin.EDIT) PARENT_RECORD else null,
            originalRecordId = if (origin == ImageOrigin.EDIT) ORIGINAL_RECORD else null,
            operations = if (origin == ImageOrigin.EDIT) "grayscale" else null,
            walletAddress = WALLET.lowercase(),
            deviceId = DEVICE_ID,
            signatureHex = SIGNATURE.toHexString(),
            status = status,
            txHash = if (reason == FailureReason.TIMEOUT) OLD_TX else null,
            submittedAt = if (reason == FailureReason.TIMEOUT) NOW.toEpochMilli() else null,
            failureReason = if (status == RegistrationStatus.FAILED) reason else null,
            createdAt = NOW.toEpochMilli()
        )
    )

    private suspend fun image(localId: Long): LocalImageEntity = images.getById(localId).shouldNotBeNull()

    @Test
    fun `hash já na rede confirma com esse recordId sem abrir a carteira`() = runTest {
        val localId = failed(FailureReason.TIMEOUT)
        // A transação que estourou o prazo foi confirmada depois
        coEvery { reader.getIdBySha256(SHA) } returns EXISTING_RECORD

        val result = useCase(localId, WALLET)

        result shouldBe CaptureResult.Confirmed(localId, EXISTING_RECORD)
        image(localId).run {
            status shouldBe RegistrationStatus.CONFIRMED
            recordId shouldBe EXISTING_RECORD
            // Na captura, a original é ela mesma
            originalRecordId shouldBe EXISTING_RECORD
            failureReason.shouldBeNull()
        }
        coVerify(exactly = 0) { ensureDeviceBound(any(), any()) }
        coVerify(exactly = 0) { keyManager.sign(any(), any()) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
    }

    @Test
    fun `edição confirmada pelo hash mantém a original da cadeia`() = runTest {
        val localId = failed(origin = ImageOrigin.EDIT)
        coEvery { reader.getIdBySha256(SHA) } returns EXISTING_RECORD

        useCase(localId, WALLET)

        image(localId).run {
            status shouldBe RegistrationStatus.CONFIRMED
            recordId shouldBe EXISTING_RECORD
            originalRecordId shouldBe ORIGINAL_RECORD
        }
    }

    @Test
    fun `hash fora da rede reabre a carteira reutilizando a assinatura do aparelho`() = runTest {
        val localId = failed(FailureReason.TIMEOUT)
        val progress = mutableListOf<RegistrationProgress>()

        val result = useCase(localId, WALLET) { progress += it }

        result shouldBe CaptureResult.Submitted(localId, TX)
        // Passou por AWAITING_WALLET (a carteira abriu com o custo estimado) e terminou em PENDING
        progress shouldContainExactly listOf(RegistrationProgress.AwaitingWallet(ENOUGH))
        image(localId).run {
            status shouldBe RegistrationStatus.PENDING
            txHash shouldBe TX
            signatureHex shouldBe SIGNATURE.toHexString()
            failureReason.shouldBeNull()
        }
        coVerify(exactly = 0) { keyManager.sign(any(), any()) }
        coVerify(exactly = 1) {
            wallet.sendTransaction(CONTRACT, RegistryTxBuilder.registerCapture(SHA, PHASH, DEVICE_ID, SIGNATURE))
        }
    }

    @Test
    fun `erro de rede na checagem mantém FAILED(NETWORK) sem enviar`() = runTest {
        val localId = failed(FailureReason.REJECTED)
        coEvery { reader.getIdBySha256(SHA) } throws RegistryException.Network(IOException("sem conexão"))

        val result = useCase(localId, WALLET)

        result shouldBe CaptureResult.Failed(localId, FailureReason.NETWORK)
        image(localId).run {
            status shouldBe RegistrationStatus.FAILED
            failureReason shouldBe FailureReason.NETWORK
        }
        coVerify(exactly = 0) { ensureDeviceBound(any(), any()) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
    }

    @Test
    fun `outra carteira conectada bloqueia o reenvio sem abrir a carteira nem assinar`() = runTest {
        val localId = failed(FailureReason.TIMEOUT)

        val result = useCase(localId, OTHER_WALLET)

        // A tela mostra "Conecte a carteira 0x1234…5678 usada neste registro para tentar de novo"
        result shouldBe CaptureResult.WrongWallet(localId, expected = WALLET.lowercase())
        image(localId).run {
            status shouldBe RegistrationStatus.FAILED
            failureReason shouldBe FailureReason.TIMEOUT
        }
        coVerify(exactly = 0) { reader.getIdBySha256(SHA) }
        coVerify(exactly = 0) { ensureDeviceBound(any(), any()) }
        coVerify(exactly = 0) { keyManager.sign(any(), any()) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
    }

    @Test
    fun `a carteira do registro é reconhecida sem diferenciar maiúsculas`() = runTest {
        val localId = failed(FailureReason.REJECTED)

        useCase(localId, WALLET.uppercase().replace("0X", "0x")) shouldBe CaptureResult.Submitted(localId, TX)
    }

    @Test
    fun `imagem presa em LOCAL_ONLY (app fechado antes de enviar) também é retomada`() = runTest {
        val localId = failed(status = RegistrationStatus.LOCAL_ONLY)

        useCase(localId, WALLET) shouldBe CaptureResult.Submitted(localId, TX)
        image(localId).status shouldBe RegistrationStatus.PENDING
    }

    @Test
    fun `só registros que não foram enviados são reenviados`() = runTest {
        val pending = failed(status = RegistrationStatus.PENDING)

        shouldThrow<IllegalArgumentException> { useCase(pending, WALLET) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-12T18:00:00Z")
        val CONTRACT = SignatureVectors.case("original").payload.contract
        const val WALLET = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val OTHER_WALLET = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01"
        const val DEVICE_ID = 3
        const val EXISTING_RECORD = 5L
        const val PARENT_RECORD = 2L
        const val ORIGINAL_RECORD = 1L
        const val OLD_TX = "0x1111111111111111111111111111111111111111111111111111111111111111"
        const val TX = "0x3333333333333333333333333333333333333333333333333333333333333333"

        val SHA = Sha256("a3f1".repeat(16))
        val PHASH: PHash = PHash.fromHex("fe44819b7ec4c03b")
        val SIGNATURE: ByteArray = SignatureVectors.case("original").rawHex.hexToByteArray()
        val ENOUGH =
            TxCost(gasLimit = BigInteger.valueOf(200_000), gasPrice = BigInteger.TEN, balance = BigInteger.TEN.pow(18))
    }
}
