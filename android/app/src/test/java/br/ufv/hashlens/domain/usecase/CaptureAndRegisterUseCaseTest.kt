package br.ufv.hashlens.domain.usecase

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.core.crypto.SignaturePayload
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.JpegEncoder
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.AppDatabase
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.testing.SignatureVectors
import br.ufv.hashlens.testing.inMemoryDatabase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.security.MessageDigest
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
 * T042: captura → galeria → releitura → vínculo → assinatura → carteira (plan "Captura", research R8,
 * data-model §2.3). Usa o [RegisterLocalImageUseCase] real sobre um Room em memória; codificação,
 * pHash (OpenCV nativo), galeria, contrato, Keystore e carteira são falsos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CaptureAndRegisterUseCaseTest {
    private val db: AppDatabase = inMemoryDatabase()
    private val images = db.localImageDao()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")

    private val encoder = mockk<JpegEncoder>()
    private val perceptualHasher = mockk<PerceptualHasher>()
    private val gallery = mockk<GalleryRepository>()
    private val reader = mockk<RegistryReader>()
    private val ensureDeviceBound = mockk<EnsureDeviceBoundUseCase>()
    private val keyManager = mockk<DeviceKeyManager>()
    private val wallet = mockk<WalletSession>()
    private val photo = mockk<Bitmap>()
    private val uri: Uri = Uri.parse("content://media/external/images/media/42")

    private val useCase = CaptureAndRegisterUseCase(
        encoder = encoder,
        sha256Hasher = Sha256Hasher(),
        perceptualHasher = perceptualHasher,
        gallery = gallery,
        reader = reader,
        ensureDeviceBound = ensureDeviceBound,
        registerLocalImage = RegisterLocalImageUseCase(images, keyManager, wallet, config, clock),
        images = images,
        clock = clock
    )

    /** Pipeline feliz: tudo responde; cada teste muda só o que interessa. */
    private fun happyPath() {
        every { encoder.encode(photo, ROTATION, any()) } returns JPEG
        every { perceptualHasher.hash(JPEG) } returns PHASH
        coEvery { gallery.save(JPEG, any()) } returns uri
        coEvery { gallery.read(uri) } returns JPEG
        coEvery { reader.getIdBySha256(SHA) } returns null
        coEvery { ensureDeviceBound(WALLET) } returns BoundDevice(deviceId = DEVICE_ID, newlyBound = false)
        every { keyManager.sign(WALLET, any()) } returns SIGNATURE
        coEvery { wallet.sendTransaction(any(), any()) } returns TX
    }

    private suspend fun onlyImage(): LocalImageEntity = images.getBySha256(SHA.hex).shouldNotBeNull()

    @After
    fun closeDatabase() = db.close()

    @Test
    fun `envia a transação e grava PENDING com txHash e submittedAt`() = runTest {
        happyPath()

        val result = useCase(photo, ROTATION, WALLET)

        val image = onlyImage()
        result shouldBe CaptureResult.Submitted(localId = image.localId, txHash = TX)
        image.run {
            status shouldBe RegistrationStatus.PENDING
            txHash shouldBe TX
            submittedAt shouldBe NOW.toEpochMilli()
            origin shouldBe ImageOrigin.CAPTURE
            contentUri shouldBe uri.toString()
            pHashHex shouldBe PHASH.hex
            walletAddress shouldBe WALLET.lowercase()
            deviceId shouldBe DEVICE_ID
            signatureHex shouldBe SIGNATURE.toHexString()
            failureReason.shouldBeNull()
        }
    }

    @Test
    fun `assina o payload v1 e envia registerCapture ao contrato`() = runTest {
        happyPath()

        useCase(photo, ROTATION, WALLET)

        val payload = SignaturePayload(
            chainId = config.chainId,
            contract = CONTRACT,
            registrant = WALLET,
            deviceId = DEVICE_ID,
            sha256 = SHA,
            pHash = PHASH,
            parentId = 0,
            operations = ""
        ).toByteArray()
        coVerify(exactly = 1) { keyManager.sign(WALLET, match { it.contentEquals(payload) }) }
        coVerify(exactly = 1) {
            wallet.sendTransaction(CONTRACT, RegistryTxBuilder.registerCapture(SHA, PHASH, DEVICE_ID, SIGNATURE))
        }
    }

    @Test
    fun `passa por LOCAL_ONLY e AWAITING_WALLET antes de PENDING`() = runTest {
        happyPath()
        val seen = mutableListOf<RegistrationStatus>()
        // Status gravado quando o vínculo é conferido e quando a carteira é aberta
        coEvery { ensureDeviceBound(WALLET) } coAnswers {
            seen += onlyImage().status
            BoundDevice(deviceId = DEVICE_ID, newlyBound = false)
        }
        coEvery { wallet.sendTransaction(any(), any()) } coAnswers {
            seen += onlyImage().status
            TX
        }

        useCase(photo, ROTATION, WALLET)

        seen shouldBe listOf(RegistrationStatus.LOCAL_ONLY, RegistrationStatus.AWAITING_WALLET)
        onlyImage().status shouldBe RegistrationStatus.PENDING
    }

    @Test
    fun `aborta com HASH_MISMATCH se o arquivo relido da galeria for diferente`() = runTest {
        happyPath()
        coEvery { gallery.read(uri) } returns JPEG + byteArrayOf(0)

        val result = useCase(photo, ROTATION, WALLET)

        val image = onlyImage()
        result shouldBe CaptureResult.Failed(localId = image.localId, reason = FailureReason.HASH_MISMATCH)
        image.status shouldBe RegistrationStatus.FAILED
        image.failureReason shouldBe FailureReason.HASH_MISMATCH
        coVerify(exactly = 0) { ensureDeviceBound(any()) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
    }

    @Test
    fun `rejeição na carteira leva a FAILED(REJECTED) e guarda a assinatura para o reenvio`() = runTest {
        happyPath()
        coEvery { wallet.sendTransaction(any(), any()) } throws WalletException.Rejected()

        val result = useCase(photo, ROTATION, WALLET)

        val image = onlyImage()
        result shouldBe CaptureResult.Failed(localId = image.localId, reason = FailureReason.REJECTED)
        image.status shouldBe RegistrationStatus.FAILED
        image.failureReason shouldBe FailureReason.REJECTED
        image.txHash.shouldBeNull()
        image.submittedAt.shouldBeNull()
        // A assinatura do aparelho é gerada uma vez e reaproveitada no reenvio (data-model §2.1)
        image.signatureHex shouldBe SIGNATURE.toHexString()
    }

    @Test
    fun `SHA já registrado não gera transação e devolve AlreadyRegistered`() = runTest {
        happyPath()
        coEvery { reader.getIdBySha256(SHA) } returns EXISTING_RECORD

        val result = useCase(photo, ROTATION, WALLET)

        result.shouldBeInstanceOf<CaptureResult.AlreadyRegistered>().recordId shouldBe EXISTING_RECORD
        coVerify(exactly = 0) { ensureDeviceBound(any()) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
        images.getByStatus(listOf(RegistrationStatus.AWAITING_WALLET, RegistrationStatus.PENDING)).shouldBeEmpty()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-09T18:00:00Z")
        val CONTRACT = SignatureVectors.case("original").payload.contract
        const val WALLET = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val ROTATION = 90
        const val DEVICE_ID = 3
        const val EXISTING_RECORD = 5L
        const val TX = "0x3333333333333333333333333333333333333333333333333333333333333333"

        // Bytes "da câmera": o SHA-256 é calculado de verdade sobre eles
        val JPEG: ByteArray = "bytes-jpeg-de-teste".encodeToByteArray()
        val SHA = Sha256(MessageDigest.getInstance("SHA-256").digest(JPEG).toHexString())
        val PHASH: PHash = PHash.fromHex("fe44819b7ec4c03b")
        val SIGNATURE: ByteArray = SignatureVectors.case("original").rawHex.hexToByteArray()
    }
}
