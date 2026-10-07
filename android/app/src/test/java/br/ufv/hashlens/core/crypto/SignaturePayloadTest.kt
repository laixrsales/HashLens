package br.ufv.hashlens.core.crypto

import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.testing.SignatureVectors
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** T015: signature-payload §1 (195 bytes, campos em ordem fixa, inteiros big-endian). */
class SignaturePayloadTest {
    // Valores distintos em cada campo para localizar erros de ordem ou de endianness
    private val payload = SignaturePayload(
        chainId = 11_155_111L,
        contract = "0xF2f42B34414936e456c6AC10c4844f456CAD65Ba",
        registrant = "0x1234567890AbcdEF1234567890aBcdef12345678",
        deviceId = 0x01020304,
        sha256 = Sha256("55824746f88c258e4ef10a10027ba7751e85b67c866f63023bdf90c2207aa527"),
        pHash = PHash(0xfe44819b7ec4c03buL),
        parentId = 258L,
        operations = "brightness:+15;grayscale"
    )
    private val bytes = payload.toByteArray()

    private fun field(from: Int, size: Int): String = bytes.copyOfRange(from, from + size).toHexString()

    @Test
    fun `tem 195 bytes`() {
        bytes.size shouldBe 195
        SignaturePayload.SIZE shouldBe 195
    }

    @Test
    fun `campo 1 domain é o ASCII HASHLENS-IMG-v1`() {
        bytes.copyOfRange(0, 15).decodeToString() shouldBe "HASHLENS-IMG-v1"
    }

    @Test
    fun `campo 2 chainId em 32 bytes big-endian`() {
        field(15, 32) shouldBe "00".repeat(28) + "00aa36a7"
    }

    @Test
    fun `campos 3 e 4 contract e registrant em 20 bytes, sem depender de maiúsculas`() {
        field(47, 20) shouldBe "f2f42b34414936e456c6ac10c4844f456cad65ba"
        field(67, 20) shouldBe "1234567890abcdef1234567890abcdef12345678"
    }

    @Test
    fun `campo 5 deviceId em 4 bytes big-endian`() {
        field(87, 4) shouldBe "01020304"
    }

    @Test
    fun `campo 6 sha256Hash em 32 bytes`() {
        field(91, 32) shouldBe payload.sha256.hex
    }

    @Test
    fun `campo 7 pHash em 8 bytes big-endian sem sinal`() {
        field(123, 8) shouldBe "fe44819b7ec4c03b"
    }

    @Test
    fun `campo 8 parentId em 32 bytes big-endian`() {
        field(131, 32) shouldBe "00".repeat(30) + "0102"
    }

    @Test
    fun `campo 9 operationsHash é o SHA-256 dos bytes UTF-8 de operations`() {
        field(163, 32) shouldBe "e72892caf6fae73df06af17f124d33870065e8f69dc004935819e22b35534d72"
    }

    @Test
    fun `operationsHash de string vazia (originais) é o SHA-256 da mensagem vazia`() {
        val original = payload.copy(parentId = 0L, operations = "").toByteArray()

        original.copyOfRange(163, 195).toHexString() shouldBe
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        original.copyOfRange(131, 163).toHexString() shouldBe "00".repeat(32)
    }

    @ParameterizedTest
    @ValueSource(strings = ["original", "edicao"])
    fun `bate byte a byte com a implementação de referência`(name: String) {
        val case = SignatureVectors.case(name)

        case.payload.toByteArray().toHexString() shouldBe case.payloadHex
        case.payload.toByteArray().copyOfRange(163, 195).toHexString() shouldBe case.operationsHash
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "F2f42B34414936e456c6AC10c4844f456CAD65Ba", // sem 0x
            "0xF2f42B34414936e456c6AC10c4844f456CAD65", // 19 bytes
            "0xF2f42B34414936e456c6AC10c4844f456CAD65Bz" // não hexadecimal
        ]
    )
    fun `endereço inválido é rejeitado`(address: String) {
        shouldThrow<IllegalArgumentException> { payload.copy(contract = address).toByteArray() }
        shouldThrow<IllegalArgumentException> { payload.copy(registrant = address).toByteArray() }
    }

    @Test
    fun `parentId e chainId negativos são rejeitados`() {
        shouldThrow<IllegalArgumentException> { payload.copy(parentId = -1L).toByteArray() }
        shouldThrow<IllegalArgumentException> { payload.copy(chainId = -1L).toByteArray() }
    }
}
