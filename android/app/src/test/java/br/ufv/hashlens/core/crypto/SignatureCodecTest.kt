package br.ufv.hashlens.core.crypto

import br.ufv.hashlens.testing.SignatureVectors
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** T016: signature-payload §2 e §3 (DER ↔ `r‖s` de 64 bytes, normalização low-S). */
class SignatureCodecTest {
    private val halfOrder = P256_ORDER.shiftRight(1)

    private fun s(raw: ByteArray) = BigInteger(1, raw.copyOfRange(32, 64))

    @ParameterizedTest
    @ValueSource(strings = ["original", "edicao"])
    fun `DER vira r‖s de 64 bytes`(name: String) {
        val case = SignatureVectors.case(name)

        SignatureCodec.derToRaw(case.derHex.hexToByteArray()).toHexString() shouldBe case.rawHex
    }

    @ParameterizedTest
    @ValueSource(strings = ["original", "edicao"])
    fun `r‖s vira o DER canônico`(name: String) {
        // "edicao" tem r < 2^255, então o INTEGER não leva o 0x00 de sinal (30 44 02 20 ...)
        val case = SignatureVectors.case(name)

        SignatureCodec.rawToDer(case.rawHex.hexToByteArray()).toHexString() shouldBe case.derHex
    }

    @ParameterizedTest
    @ValueSource(strings = ["original", "edicao"])
    fun `s alto é normalizado para n menos s`(name: String) {
        val case = SignatureVectors.case(name)

        val raw = SignatureCodec.derToRaw(case.highSDerHex.hexToByteArray())

        raw.toHexString() shouldBe case.rawHex
        s(raw).compareTo(halfOrder) shouldBeLessThanOrEqual 0
    }

    @ParameterizedTest
    @ValueSource(strings = ["original", "edicao"])
    fun `assinatura normalizada é aceita na verificação`(name: String) {
        val case = SignatureVectors.case(name)
        val normalized = SignatureCodec.derToRaw(case.highSDerHex.hexToByteArray())

        SignatureVectors.verify(case.payloadHex.hexToByteArray(), SignatureCodec.rawToDer(normalized)) shouldBe true
    }

    @Test
    fun `mesma assinatura com pHash alterado é rejeitada`() {
        val der = SignatureCodec.rawToDer(SignatureVectors.case("original").rawHex.hexToByteArray())

        SignatureVectors.verify(SignatureVectors.tamperedPayloadHex.hexToByteArray(), der) shouldBe false
    }

    @Test
    fun `inteiros curtos ganham zeros à esquerda e voltam ao DER mínimo`() {
        val raw = ByteArray(64).apply {
            this[31] = 1 // r = 1
            this[63] = 0x7f // s = 127
        }
        val der = "300602010102017f" // SEQUENCE { INTEGER 1, INTEGER 127 }

        SignatureCodec.rawToDer(raw).toHexString() shouldBe der
        SignatureCodec.derToRaw(der.hexToByteArray()) shouldBe raw
    }

    @Test
    fun `r‖s com tamanho diferente de 64 bytes é rejeitado`() {
        shouldThrow<IllegalArgumentException> { SignatureCodec.rawToDer(ByteArray(63)) }
        shouldThrow<IllegalArgumentException> { SignatureCodec.rawToDer(ByteArray(65)) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "", // vazio
            "3006020101020101ff", // bytes sobrando
            "3106020101020101", // não é SEQUENCE
            "300602010103010101", // segundo elemento não é INTEGER
            "3007020101020201" // comprimento declarado maior que o conteúdo
        ]
    )
    fun `DER malformado é rejeitado`(derHex: String) {
        shouldThrow<IllegalArgumentException> { SignatureCodec.derToRaw(derHex.hexToByteArray()) }
    }

    private companion object {
        val P256_ORDER = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
    }
}
