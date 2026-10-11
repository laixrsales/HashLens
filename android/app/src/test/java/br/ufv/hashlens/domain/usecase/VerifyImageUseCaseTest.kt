package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.core.crypto.SignatureCodec
import br.ufv.hashlens.core.crypto.SignaturePayload
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.domain.model.EditOperationCodec
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.RecordView
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.domain.model.VerificationResult
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * T059: algoritmo de verification-result §1 sobre um contrato falso.
 *
 * O [RegistryReader] responde a partir de mapas em memória e o pHash (OpenCV nativo) é falso; o
 * SHA-256 e a verificação de assinatura são os reais. Os registros são assinados no teste com
 * chaves P-256 geradas na hora, sobre o mesmo payload v1 que o app assina (signature-payload §1).
 */
class VerifyImageUseCaseTest {
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")
    private val perceptualHasher = mockk<PerceptualHasher>()
    private val reader = mockk<RegistryReader>()

    private val records = mutableMapOf<Long, ImageRecord>()
    private val devices = mutableMapOf<Int, Device>()
    private val keys = mutableMapOf<Int, KeyPair>()

    private val useCase = VerifyImageUseCase(
        sha256Hasher = Sha256Hasher(),
        perceptualHasher = perceptualHasher,
        reader = reader,
        signatureVerifier = DeviceSignatureVerifier(config)
    )

    init {
        every { perceptualHasher.hash(any()) } returns QUERY_PHASH
        // Stubs pelos hashes concretos da consulta: com value classes, o any() do mockk monta um Sha256
        // com hex aleatório (que o init recusa) e o answers recebe o valor sem o invólucro
        coEvery { reader.getIdBySha256(sha256Of(FILE)) } answers {
            records.values.firstOrNull { it.sha256 == sha256Of(FILE) }?.id
        }
        coEvery { reader.getIdsByPHash(QUERY_PHASH) } answers {
            records.values.filter { it.pHash == QUERY_PHASH }.map { it.id }.sorted()
        }
        coEvery { reader.getRecord(any()) } answers { records[firstArg()] }
        coEvery { reader.getDevice(any()) } answers { devices[firstArg()] }
        coEvery { reader.getDevicesOf(any()) } answers {
            devices.values.filter { it.owner.equals(firstArg(), ignoreCase = true) }.map { it.id }
        }
        bindDevice(DEVICE_A, WALLET_A)
        bindDevice(DEVICE_B, WALLET_B)
    }

    @Test
    fun `arquivo idêntico a uma original registrada é Registrada – original`() = runTest {
        val original = register(id = 1, sha256 = sha256Of(FILE), pHash = QUERY_PHASH)

        val result = useCase(FILE)

        result shouldBe VerificationResult.RegisteredOriginal(view(original))
    }

    @Test
    fun `RecordView traz hashes em hex, operações decodificadas e nenhum link de transação`() = runTest {
        register(id = 1, sha256 = sha256Of(OTHER_FILE), pHash = ORIGINAL_PHASH)
        register(
            id = 2,
            sha256 = sha256Of(FILE),
            pHash = QUERY_PHASH,
            parentId = 1,
            originalId = 1,
            registrant = WALLET_B,
            deviceId = DEVICE_B,
            operations = "brightness:+30;grayscale"
        )

        val edited = useCase(FILE).shouldBeInstanceOf<VerificationResult.RegisteredEdited>()

        edited.record.run {
            id shouldBe 2L
            sha256 shouldBe sha256Of(FILE).hex
            pHash shouldBe QUERY_PHASH.hex
            registrant shouldBe WALLET_B
            deviceId shouldBe DEVICE_B
            timestamp shouldBe timestampOf(2)
            operations shouldContainExactly listOf(EditOperation.Brightness(30), EditOperation.Grayscale)
            // getRecord não informa a transação que criou o registro
            txUrl shouldBe null
        }
    }

    @Test
    fun `versão editada traz a original e o percentual de alteração em relação a ela`() = runTest {
        val original = register(id = 1, sha256 = sha256Of(OTHER_FILE), pHash = ORIGINAL_PHASH)
        val middle = register(id = 2, sha256 = sha(2), pHash = MIDDLE_PHASH, parentId = 1, originalId = 1)
        // Edição da edição: o percentual é contra a original (id 1), não contra o pai (id 2)
        val edition = register(
            id = 3,
            sha256 = sha256Of(FILE),
            pHash = QUERY_PHASH,
            parentId = middle.id,
            originalId = original.id,
            registrant = WALLET_B,
            deviceId = DEVICE_B,
            operations = "grayscale"
        )

        val result = useCase(FILE)

        // QUERY_PHASH difere de ORIGINAL_PHASH em 8 bits: 8 / 64 × 100 = 12,5%
        result shouldBe VerificationResult.RegisteredEdited(
            record = view(edition),
            original = view(original),
            percentFromOriginal = 12.5
        )
    }

    @Test
    fun `versão editada sem alteração visual tem percentual zero`() = runTest {
        val original = register(id = 1, sha256 = sha256Of(OTHER_FILE), pHash = QUERY_PHASH)
        val edition = register(id = 2, sha256 = sha256Of(FILE), pHash = QUERY_PHASH, parentId = 1, originalId = 1)

        useCase(FILE) shouldBe VerificationResult.RegisteredEdited(view(edition), view(original), 0.0)
    }

    @Test
    fun `assinatura inválida no SHA exato é Registro adulterado`() = runTest {
        val tampered = register(id = 1, sha256 = sha256Of(FILE), pHash = QUERY_PHASH, validSignature = false)

        useCase(FILE) shouldBe VerificationResult.Tampered(view(tampered))
    }

    @Test
    fun `aparelho de outra carteira não valida a assinatura`() = runTest {
        // Assinado pela chave do aparelho B, mas registrado em nome da carteira A
        val record = register(
            id = 1,
            sha256 = sha256Of(FILE),
            pHash = QUERY_PHASH,
            registrant = WALLET_A,
            deviceId = DEVICE_B
        )

        useCase(FILE) shouldBe VerificationResult.Tampered(view(record))
    }

    @Test
    fun `registro assinado por aparelho antigo da mesma carteira continua válido (R25)`() = runTest {
        // A carteira A perdeu a chave do aparelho A e vinculou um novo; o registro antigo segue válido
        bindDevice(DEVICE_A_NEW, WALLET_A)
        val old = register(id = 1, sha256 = sha256Of(FILE), pHash = QUERY_PHASH, deviceId = DEVICE_A)
        register(id = 2, sha256 = sha(2), pHash = ORIGINAL_PHASH, deviceId = DEVICE_A_NEW)

        val result = useCase(FILE)

        result shouldBe VerificationResult.RegisteredOriginal(view(old))
        coVerify { reader.getDevice(DEVICE_A) }
    }

    @Test
    fun `sem SHA exato e com pHash igual é Correspondência visual, em ordem cronológica`() = runTest {
        val original = register(id = 1, sha256 = sha(1), pHash = ORIGINAL_PHASH)
        val edition = register(
            id = 2,
            sha256 = sha(2),
            pHash = QUERY_PHASH,
            parentId = 1,
            originalId = 1,
            registrant = WALLET_B,
            deviceId = DEVICE_B
        )
        val otherOriginal = register(id = 3, sha256 = sha(3), pHash = QUERY_PHASH)
        // O contrato devolve os ids fora de ordem; a ordem exibida é a do registro (timestamp)
        coEvery { reader.getIdsByPHash(QUERY_PHASH) } returns listOf(3L, 2L)

        val result = useCase(FILE)

        result shouldBe VerificationResult.VisualMatch(
            listOf(
                VerificationResult.VisualMatch.Candidate(view(edition), view(original), 12.5),
                VerificationResult.VisualMatch.Candidate(view(otherOriginal), view(otherOriginal), 0.0)
            )
        )
    }

    @Test
    fun `candidatos com assinatura inválida ficam fora da Correspondência visual`() = runTest {
        register(id = 1, sha256 = sha(1), pHash = QUERY_PHASH, validSignature = false)
        val valid = register(id = 2, sha256 = sha(2), pHash = QUERY_PHASH)

        val result = useCase(FILE)

        result shouldBe VerificationResult.VisualMatch(
            listOf(VerificationResult.VisualMatch.Candidate(view(valid), view(valid), 0.0))
        )
    }

    @Test
    fun `se todos os candidatos visuais forem inválidos o resultado é adulterado do primeiro`() = runTest {
        val first = register(id = 1, sha256 = sha(1), pHash = QUERY_PHASH, validSignature = false)
        register(id = 2, sha256 = sha(2), pHash = QUERY_PHASH, validSignature = false)

        useCase(FILE) shouldBe VerificationResult.Tampered(view(first))
    }

    @Test
    fun `sem SHA exato nem pHash igual é Não registrada`() = runTest {
        register(id = 1, sha256 = sha(1), pHash = ORIGINAL_PHASH)

        useCase(FILE) shouldBe VerificationResult.NotRegistered
    }

    @Test
    fun `SHA exato tem prioridade sobre correspondências visuais`() = runTest {
        register(id = 1, sha256 = sha(1), pHash = QUERY_PHASH)
        val exact = register(id = 2, sha256 = sha256Of(FILE), pHash = QUERY_PHASH)

        useCase(FILE) shouldBe VerificationResult.RegisteredOriginal(view(exact))
        coVerify(exactly = 0) { reader.getIdsByPHash(QUERY_PHASH) }
    }

    @Test
    fun `falha de rede na busca por SHA é Não foi possível verificar, nunca Não registrada`() = runTest {
        coEvery { reader.getIdBySha256(sha256Of(FILE)) } throws RegistryException.Network(IOException("offline"))

        useCase(FILE) shouldBe VerificationResult.Error(Reason.NETWORK)
    }

    @Test
    fun `falha de rede na busca por pHash é Não foi possível verificar`() = runTest {
        coEvery { reader.getIdsByPHash(QUERY_PHASH) } throws RegistryException.Network(IOException("offline"))

        useCase(FILE) shouldBe VerificationResult.Error(Reason.NETWORK)
    }

    @Test
    fun `falha de rede ao carregar um candidato é Não foi possível verificar`() = runTest {
        register(id = 1, sha256 = sha(1), pHash = QUERY_PHASH)
        coEvery { reader.getDevice(any()) } throws RegistryException.Network(IOException("offline"))

        useCase(FILE) shouldBe VerificationResult.Error(Reason.NETWORK)
    }

    @Test
    fun `arquivo que não decodifica é Formato não suportado, sem consultar o contrato`() = runTest {
        every { perceptualHasher.hash(any()) } throws IllegalArgumentException("arquivo não é uma imagem decodificável")

        useCase(HEIC_LIKE) shouldBe VerificationResult.Error(Reason.UNSUPPORTED_FILE)
        coVerify { reader wasNot Called }
    }

    private fun bindDevice(id: Int, owner: String) {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        keys[id] = pair
        devices[id] = Device(id = id, owner = owner, publicKey = uncompressed(pair.public as ECPublicKey))
    }

    /** Grava um registro no contrato falso, assinado pela chave do aparelho [deviceId]. */
    private fun register(
        id: Long,
        sha256: Sha256,
        pHash: PHash,
        parentId: Long? = null,
        originalId: Long = id,
        registrant: String = WALLET_A,
        deviceId: Int = DEVICE_A,
        operations: String = "",
        validSignature: Boolean = true
    ): ImageRecord {
        val payload = SignaturePayload(
            chainId = config.chainId,
            contract = CONTRACT,
            registrant = registrant,
            deviceId = deviceId,
            sha256 = sha256,
            // Assinatura inválida: assina outro pHash, como se os dados on-chain tivessem sido trocados
            pHash = if (validSignature) pHash else PHash(pHash.value xor 1uL),
            parentId = parentId ?: 0L,
            operations = operations
        )
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.getValue(deviceId).private)
            update(payload.toByteArray())
            sign()
        }
        return ImageRecord(
            id = id,
            sha256 = sha256,
            pHash = pHash,
            parentId = parentId,
            originalId = originalId,
            registrant = registrant,
            deviceId = deviceId,
            signature = SignatureCodec.derToRaw(der),
            timestamp = timestampOf(id),
            operations = operations
        ).also { records[id] = it }
    }

    private fun view(record: ImageRecord) = RecordView(
        id = record.id,
        sha256 = record.sha256.hex,
        pHash = record.pHash.hex,
        registrant = record.registrant,
        deviceId = record.deviceId,
        timestamp = record.timestamp,
        operations = EditOperationCodec.decode(record.operations),
        txUrl = null
    )

    private companion object {
        const val CONTRACT = "0xF2f42B34414936e456c6AC10c4844f456CAD65Ba"
        const val WALLET_A = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val WALLET_B = "0xABCDEFabcdef0123456789ABCDEFabcdef012345"
        const val DEVICE_A = 1
        const val DEVICE_B = 2
        const val DEVICE_A_NEW = 3
        const val COORDINATE_SIZE = 32

        val FILE = "arquivo verificado".toByteArray()
        val OTHER_FILE = "arquivo original".toByteArray()
        val HEIC_LIKE = byteArrayOf(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63)

        val QUERY_PHASH = PHash.fromHex("fe44819b7ec4c03b")

        // 8 bits trocados em relação a QUERY_PHASH (12,5%) e 4 bits em relação ao meio da cadeia
        val ORIGINAL_PHASH = PHash(QUERY_PHASH.value xor 0xffuL)
        val MIDDLE_PHASH = PHash(QUERY_PHASH.value xor 0x0fuL)

        fun sha256Of(bytes: ByteArray) = Sha256(MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())

        /** SHA-256 qualquer, distinto por [n] e diferente do de [FILE]. */
        fun sha(n: Int) = sha256Of("registro $n".toByteArray())

        fun timestampOf(id: Long): Instant = Instant.parse("2026-10-01T12:00:00Z").plusSeconds(id * 3_600)

        fun uncompressed(key: ECPublicKey): ByteArray = byteArrayOf(0x04) + fixed(key.w.affineX) + fixed(key.w.affineY)

        fun fixed(value: BigInteger): ByteArray = value.toByteArray().takeLast(COORDINATE_SIZE).toByteArray().let {
            ByteArray(COORDINATE_SIZE - it.size) + it
        }
    }
}
