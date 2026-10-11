package br.ufv.hashlens.domain.usecase

import android.app.Application
import android.net.Uri
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.core.crypto.SignaturePayload
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.EditRenderer
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.chain.TxCost
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.AppDatabase
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.testing.SignatureVectors
import br.ufv.hashlens.testing.inMemoryDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.FileNotFoundException
import java.math.BigInteger
import java.nio.ByteBuffer
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
 * T068: edição → galeria → vínculo → assinatura → `registerEdit` (US3, plan "Edição", data-model
 * §2.1). Usa o [RegisterLocalImageUseCase] real sobre um Room em memória; a renderização dos
 * filtros (OpenCV nativo), o pHash, a galeria, o contrato, o Keystore e a carteira são falsos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EditAndRegisterUseCaseTest {
    private val db: AppDatabase = inMemoryDatabase()
    private val images = db.localImageDao()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")

    private val renderer = mockk<EditRenderer>()
    private val perceptualHasher = mockk<PerceptualHasher>()
    private val gallery = mockk<GalleryRepository>()
    private val reader = mockk<RegistryReader>()
    private val ensureDeviceBound = mockk<EnsureDeviceBoundUseCase>()
    private val keyManager = mockk<DeviceKeyManager>()
    private val wallet = mockk<WalletSession>()
    private val txBuilder = mockk<RegistryTxBuilder>()
    private val parentUri: Uri = Uri.parse("content://media/external/images/media/10")
    private val editedUri: Uri = Uri.parse("content://media/external/images/media/11")

    private val useCase = EditAndRegisterUseCase(
        renderer = renderer,
        sha256Hasher = Sha256Hasher(),
        perceptualHasher = perceptualHasher,
        gallery = gallery,
        reader = reader,
        ensureDeviceBound = ensureDeviceBound,
        registerLocalImage = RegisterLocalImageUseCase(images, keyManager, wallet, txBuilder, config, clock),
        images = images,
        clock = clock
    )

    @After
    fun closeDatabase() = db.close()

    /** Pai confirmado no acervo: a captura original (registro 1) ou uma edição dela. */
    private suspend fun parent(
        status: RegistrationStatus = RegistrationStatus.CONFIRMED,
        origin: ImageOrigin = ImageOrigin.CAPTURE,
        recordId: Long? = PARENT_RECORD,
        originalRecordId: Long? = PARENT_RECORD
    ): Long = images.insert(
        LocalImageEntity(
            contentUri = parentUri.toString(),
            sha256Hex = PARENT_SHA.hex,
            pHashHex = "c3d2e1f0a9b8c7d6",
            origin = origin,
            parentRecordId = if (origin == ImageOrigin.EDIT) originalRecordId else null,
            originalRecordId = originalRecordId,
            operations = if (origin == ImageOrigin.EDIT) "sepia" else null,
            walletAddress = WALLET.lowercase(),
            deviceId = DEVICE_ID,
            status = status,
            recordId = recordId,
            createdAt = NOW.toEpochMilli()
        )
    )

    /** Pipeline feliz: tudo responde; cada teste muda só o que interessa. */
    private fun happyPath() {
        coEvery { gallery.read(parentUri) } returns PARENT_JPEG
        every { renderer.render(PARENT_JPEG, any(), any()) } returns EDITED_JPEG
        every { perceptualHasher.hash(EDITED_JPEG) } returns EDITED_PHASH
        coEvery { gallery.save(EDITED_JPEG, any()) } returns editedUri
        coEvery { gallery.read(editedUri) } returns EDITED_JPEG
        coEvery { reader.getIdBySha256(EDITED_SHA) } returns null
        coEvery { ensureDeviceBound(WALLET, any()) } returns BoundDevice(deviceId = DEVICE_ID, newlyBound = false)
        every { keyManager.sign(WALLET, any()) } returns SIGNATURE
        coEvery { txBuilder.estimate(WALLET, any()) } returns ENOUGH
        coEvery { wallet.sendTransaction(any(), any()) } returns TX
    }

    private suspend fun edited(): LocalImageEntity = images.getBySha256(EDITED_SHA.hex).shouldNotBeNull()

    @Test
    fun `recusa editar uma imagem cujo registro não está confirmado`() = runTest {
        happyPath()

        val parentId = parent(status = RegistrationStatus.LOCAL_ONLY, recordId = null, originalRecordId = null)

        listOf(RegistrationStatus.LOCAL_ONLY, RegistrationStatus.PENDING, RegistrationStatus.FAILED).forEach { status ->
            images.update(requireNotNull(images.getById(parentId)).copy(status = status))

            useCase(parentId, OPERATIONS, WALLET) shouldBe EditResult.ParentNotConfirmed(status)
        }
        verify(exactly = 0) { renderer.render(any(), any(), any()) }
        coVerify(exactly = 0) { gallery.save(any(), any()) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
    }

    @Test
    fun `edita os bytes do arquivo do pai e grava a versão como EDIT com o pai e a original`() = runTest {
        happyPath()
        val parentId = parent()

        val result = useCase(parentId, OPERATIONS, WALLET)

        val image = edited()
        result shouldBe EditResult.Registration(CaptureResult.Submitted(localId = image.localId, txHash = TX))
        image.run {
            origin shouldBe ImageOrigin.EDIT
            parentRecordId shouldBe PARENT_RECORD
            originalRecordId shouldBe PARENT_RECORD
            contentUri shouldBe editedUri.toString()
            pHashHex shouldBe EDITED_PHASH.hex
            walletAddress shouldBe WALLET.lowercase()
            status shouldBe RegistrationStatus.PENDING
        }
        verify(exactly = 1) { renderer.render(PARENT_JPEG, OPERATIONS, any()) }
    }

    @Test
    fun `edição de uma edição tem a versão anterior como pai e mantém a mesma original`() = runTest {
        happyPath()
        val parentId = parent(origin = ImageOrigin.EDIT, recordId = 7, originalRecordId = 1)

        useCase(parentId, OPERATIONS, WALLET)

        edited().run {
            parentRecordId shouldBe 7L
            originalRecordId shouldBe 1L
        }
        coVerify(exactly = 1) {
            wallet.sendTransaction(
                CONTRACT,
                RegistryTxBuilder.registerEdit(7, EDITED_SHA, EDITED_PHASH, DEVICE_ID, SIGNATURE, OPERATIONS_TEXT)
            )
        }
    }

    @Test
    fun `serializa as operações na ordem em que foram aplicadas`() = runTest {
        happyPath()
        val parentId = parent()
        val reversed = OPERATIONS.reversed()

        useCase(parentId, reversed, WALLET)

        edited().operations shouldBe "grayscale;brightness:+30"
        verify(exactly = 1) { renderer.render(PARENT_JPEG, reversed, any()) }
    }

    @Test
    fun `assina o payload com o pai e o hash das operações e envia registerEdit`() = runTest {
        happyPath()
        val parentId = parent()

        useCase(parentId, OPERATIONS, WALLET)

        val payload = SignaturePayload(
            chainId = config.chainId,
            contract = CONTRACT,
            registrant = WALLET,
            deviceId = DEVICE_ID,
            sha256 = EDITED_SHA,
            pHash = EDITED_PHASH,
            parentId = PARENT_RECORD,
            operations = OPERATIONS_TEXT
        ).toByteArray()
        coVerify(exactly = 1) {
            keyManager.sign(
                WALLET,
                match { signed ->
                    signed.contentEquals(payload) &&
                        // Os últimos 64 bytes: parentId (uint256) e operationsHash = SHA-256 do texto
                        signed.copyOfRange(
                            PARENT_OFFSET,
                            OPERATIONS_HASH_OFFSET
                        ).contentEquals(uint256(PARENT_RECORD)) &&
                        signed.copyOfRange(
                            OPERATIONS_HASH_OFFSET,
                            SignaturePayload.SIZE
                        ).contentEquals(sha256(OPERATIONS_TEXT))
                }
            )
        }
        coVerify(exactly = 1) {
            wallet.sendTransaction(
                CONTRACT,
                RegistryTxBuilder.registerEdit(
                    PARENT_RECORD,
                    EDITED_SHA,
                    EDITED_PHASH,
                    DEVICE_ID,
                    SIGNATURE,
                    OPERATIONS_TEXT
                )
            )
        }
    }

    @Test
    fun `arquivo importado de outra pessoa vira versão com o pai e a original dele, sem passar pelo acervo`() =
        runTest {
            happyPath()
            val imported = ImportedParent(
                recordId = 7,
                originalId = 1,
                registrant = OTHER_WALLET,
                originalRegistrant = OTHER_WALLET,
                bytes = PARENT_JPEG
            )

            val result = useCase.editImported(imported, OPERATIONS, WALLET)

            val image = edited()
            result shouldBe EditResult.Registration(CaptureResult.Submitted(localId = image.localId, txHash = TX))
            image.run {
                origin shouldBe ImageOrigin.EDIT
                parentRecordId shouldBe 7L
                originalRecordId shouldBe 1L
                // A versão é de quem editou, não do autor da original
                walletAddress shouldBe WALLET.lowercase()
            }
            // Os bytes vêm do arquivo importado, não da galeria
            coVerify(exactly = 0) { gallery.read(parentUri) }
            coVerify(exactly = 1) {
                wallet.sendTransaction(
                    CONTRACT,
                    RegistryTxBuilder.registerEdit(7, EDITED_SHA, EDITED_PHASH, DEVICE_ID, SIGNATURE, OPERATIONS_TEXT)
                )
            }
        }

    @Test
    fun `recusa se o arquivo do pai mudou na galeria`() = runTest {
        happyPath()
        val parentId = parent()
        coEvery { gallery.read(parentUri) } returns PARENT_JPEG + byteArrayOf(0)

        val result = useCase(parentId, OPERATIONS, WALLET)

        result shouldBe EditResult.ParentUnavailable
        verify(exactly = 0) { renderer.render(any(), any(), any()) }
    }

    @Test
    fun `recusa se o arquivo do pai saiu da galeria`() = runTest {
        happyPath()
        val parentId = parent()
        coEvery { gallery.read(parentUri) } throws FileNotFoundException("removido")

        useCase(parentId, OPERATIONS, WALLET) shouldBe EditResult.ParentUnavailable
    }

    @Test
    fun `versão idêntica a um registro existente não abre a carteira`() = runTest {
        happyPath()
        val parentId = parent()
        coEvery { reader.getIdBySha256(EDITED_SHA) } returns 9L

        val result = useCase(parentId, OPERATIONS, WALLET)

        result shouldBe EditResult.Registration(CaptureResult.AlreadyRegistered(9))
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
    }

    @Test
    fun `arquivo relido diferente do gravado falha sem assinar`() = runTest {
        happyPath()
        val parentId = parent()
        coEvery { gallery.read(editedUri) } returns EDITED_JPEG + byteArrayOf(0)

        val result = useCase(parentId, OPERATIONS, WALLET)

        result.shouldBeInstanceOf<EditResult.Registration>().result.shouldBeInstanceOf<CaptureResult.Failed>()
        coVerify(exactly = 0) { keyManager.sign(any(), any()) }
    }

    @Test
    fun `sem operações não há edição a registrar`() = runTest {
        happyPath()
        val parentId = parent()

        shouldThrow<IllegalArgumentException> { useCase(parentId, emptyList(), WALLET) }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-11T18:00:00Z")
        val CONTRACT = SignatureVectors.case("original").payload.contract
        const val WALLET = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val OTHER_WALLET = "0xABCDEFabcdef0123456789ABCDEFabcdef012345"
        const val DEVICE_ID = 3
        const val PARENT_RECORD = 1L
        const val TX = "0x4444444444444444444444444444444444444444444444444444444444444444"

        val OPERATIONS = listOf(EditOperation.Brightness(30), EditOperation.Grayscale)
        const val OPERATIONS_TEXT = "brightness:+30;grayscale"

        // Bytes "da galeria" e "editados": os SHA-256 são calculados de verdade sobre eles
        val PARENT_JPEG: ByteArray = "bytes-do-pai".encodeToByteArray()
        val EDITED_JPEG: ByteArray = "bytes-editados".encodeToByteArray()
        val PARENT_SHA = Sha256(sha256(PARENT_JPEG).toHexString())
        val EDITED_SHA = Sha256(sha256(EDITED_JPEG).toHexString())
        val EDITED_PHASH: PHash = PHash.fromHex("c3d2e1f0a9b8c7f6")
        val SIGNATURE: ByteArray = SignatureVectors.case("edicao").rawHex.hexToByteArray()
        val ENOUGH =
            TxCost(gasLimit = BigInteger.valueOf(250_000), gasPrice = BigInteger.TEN, balance = BigInteger.TEN.pow(18))

        // signature-payload §1: ... ‖ parentId (32 bytes) ‖ operationsHash (32 bytes)
        const val OPERATIONS_HASH_OFFSET = SignaturePayload.SIZE - 32
        const val PARENT_OFFSET = OPERATIONS_HASH_OFFSET - 32

        fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        fun sha256(text: String): ByteArray = sha256(text.encodeToByteArray())

        fun uint256(value: Long): ByteArray = ByteBuffer.allocate(32).putLong(24, value).array()
    }
}
