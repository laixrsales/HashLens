package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * T075: importar um arquivo de outra pessoa para edição (US4, FR-015, research R18). Só uma cópia
 * exata (SHA-256) de um registro libera o editor, com o pai inequívoco; correspondência só visual é
 * recusada mesmo com um único candidato, e o pHash nem é consultado.
 *
 * O contrato é falso; a verificação da assinatura também (testada em `DeviceSignatureVerifierTest`).
 */
class ImportForEditUseCaseTest {
    private val reader = mockk<RegistryReader>()
    private val signatureVerifier = mockk<DeviceSignatureVerifier>()

    private val useCase = ImportForEditUseCase(
        sha256Hasher = Sha256Hasher(),
        reader = reader,
        signatureVerifier = signatureVerifier
    )

    private val records = mutableMapOf<Long, ImageRecord>()

    init {
        // Stub pelo SHA concreto: com value classes, o any() do mockk monta um Sha256 inválido
        coEvery { reader.getIdBySha256(FILE_SHA) } answers { records.values.firstOrNull { it.sha256 == FILE_SHA }?.id }
        coEvery { reader.getRecord(any()) } answers { records[firstArg()] }
        coEvery { reader.getDevice(any()) } answers { device(firstArg()) }
        every { signatureVerifier.verify(any(), any()) } returns true
    }

    private fun device(id: Int) = Device(
        id = id,
        owner = if (id ==
            AUTHOR_DEVICE
        ) {
            AUTHOR
        } else {
            EDITOR
        },
        publicKey = ByteArray(65)
    )

    private fun record(
        id: Long,
        sha256: Sha256,
        parentId: Long? = null,
        originalId: Long = id,
        registrant: String = AUTHOR,
        deviceId: Int = AUTHOR_DEVICE
    ) = ImageRecord(
        id = id,
        sha256 = sha256,
        pHash = PHASH,
        parentId = parentId,
        originalId = originalId,
        registrant = registrant,
        deviceId = deviceId,
        signature = ByteArray(64),
        timestamp = Instant.parse("2026-10-01T12:00:00Z").plusSeconds(id),
        operations = if (parentId == null) "" else "grayscale"
    ).also { records[id] = it }

    @Test
    fun `cópia exata de uma original libera o editor com ela como pai`() = runTest {
        record(id = 1, sha256 = FILE_SHA)

        val result = useCase(FILE)

        result shouldBe ImportResult.Eligible(
            ImportedParent(recordId = 1, originalId = 1, registrant = AUTHOR, originalRegistrant = AUTHOR, bytes = FILE)
        )
    }

    @Test
    fun `cópia exata de uma versão editada por outra pessoa traz o pai e o autor da original`() = runTest {
        record(id = 1, sha256 = sha("original"))
        record(id = 2, sha256 = FILE_SHA, parentId = 1, originalId = 1, registrant = EDITOR, deviceId = EDITOR_DEVICE)

        val parent = useCase(FILE).shouldBeInstanceOf<ImportResult.Eligible>().parent

        parent.recordId shouldBe 2L
        parent.originalId shouldBe 1L
        parent.registrant shouldBe EDITOR
        parent.originalRegistrant shouldBe AUTHOR
        parent.bytes.contentEquals(FILE) shouldBe true
    }

    @Test
    fun `arquivo sem registro é recusado`() = runTest {
        record(id = 1, sha256 = sha("outro arquivo"))

        useCase(FILE) shouldBe ImportResult.NotRegistered
    }

    @Test
    fun `correspondência só visual é recusada mesmo com um único candidato, sem consultar o pHash`() = runTest {
        // Um registro com o mesmo pHash do arquivo, mas outro SHA-256 (ex.: cópia recomprimida)
        record(id = 1, sha256 = sha("original antes da recompressão"))
        coEvery { reader.getIdsByPHash(any()) } returns listOf(1L)

        useCase(FILE) shouldBe ImportResult.NotRegistered
        coVerify(exactly = 0) { reader.getIdsByPHash(any()) }
    }

    @Test
    fun `pai com assinatura inválida não é editável`() = runTest {
        record(id = 1, sha256 = FILE_SHA)
        every { signatureVerifier.verify(any(), any()) } returns false

        useCase(FILE) shouldBe ImportResult.Tampered(recordId = 1)
    }

    @Test
    fun `original de uma versão com assinatura inválida não impede a edição da versão`() = runTest {
        // A versão importada é o pai; só a assinatura dela decide (a original aparece só como autoria)
        val original = record(id = 1, sha256 = sha("original"))
        record(id = 2, sha256 = FILE_SHA, parentId = 1, originalId = 1, registrant = EDITOR, deviceId = EDITOR_DEVICE)
        every { signatureVerifier.verify(original, any()) } returns false

        useCase(FILE).shouldBeInstanceOf<ImportResult.Eligible>().parent.recordId shouldBe 2L
    }

    @Test
    fun `falha de rede é Error NETWORK, nunca recusa`() = runTest {
        coEvery { reader.getIdBySha256(FILE_SHA) } throws RegistryException.Network(IOException("offline"))

        useCase(FILE) shouldBe ImportResult.Error(Reason.NETWORK)
    }

    @Test
    fun `erro do nó ou registro inconsistente é Error UNKNOWN`() = runTest {
        coEvery { reader.getIdBySha256(FILE_SHA) } throws RegistryException.Rpc(-32000, "execution reverted")
        useCase(FILE) shouldBe ImportResult.Error(Reason.UNKNOWN)

        // O contrato aponta um id que não devolve
        coEvery { reader.getIdBySha256(FILE_SHA) } returns 99L
        useCase(FILE) shouldBe ImportResult.Error(Reason.UNKNOWN)
    }

    private companion object {
        const val AUTHOR = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val EDITOR = "0xABCDEFabcdef0123456789ABCDEFabcdef012345"
        const val AUTHOR_DEVICE = 1
        const val EDITOR_DEVICE = 2

        val FILE = "arquivo recebido".toByteArray()
        val FILE_SHA = sha256Of(FILE)
        val PHASH = PHash.fromHex("fe44819b7ec4c03b")

        fun sha256Of(bytes: ByteArray) = Sha256(MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())

        fun sha(text: String) = sha256Of(text.toByteArray())
    }
}
