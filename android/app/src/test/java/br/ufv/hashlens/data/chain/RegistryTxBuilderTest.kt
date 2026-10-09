package br.ufv.hashlens.data.chain

import br.ufv.hashlens.testing.CalldataVectors
import br.ufv.hashlens.testing.SignatureVectors
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * T043: calldata das funções de escrita igual ao de `cast calldata` para os mesmos argumentos
 * (`tools/chain/gerar_calldata.sh`). Assinatura do aparelho entra como `r ‖ s` (64 bytes).
 */
class RegistryTxBuilderTest {
    private val original = SignatureVectors.case("original")
    private val edit = SignatureVectors.case("edicao")

    @Test
    fun `registerDevice com a chave pública de 65 bytes`() {
        RegistryTxBuilder.registerDevice(SignatureVectors.publicKeyBytes) shouldBe
            CalldataVectors.calldata("registerDevice")
    }

    @Test
    fun `registerCapture divide a assinatura em r e s`() {
        val payload = original.payload
        RegistryTxBuilder.registerCapture(
            sha256 = payload.sha256,
            pHash = payload.pHash,
            deviceId = payload.deviceId,
            signature = original.rawHex.hexToByteArray()
        ) shouldBe CalldataVectors.calldata("registerCapture original")
    }

    @Test
    fun `registerEdit inclui o pai e a string de operações`() {
        val payload = edit.payload
        RegistryTxBuilder.registerEdit(
            parentId = payload.parentId,
            sha256 = payload.sha256,
            pHash = payload.pHash,
            deviceId = payload.deviceId,
            signature = edit.rawHex.hexToByteArray(),
            operations = payload.operations
        ) shouldBe CalldataVectors.calldata("registerEdit edicao")
    }

    @Test
    fun `pHash com o bit mais alto ligado vira uint64 sem sinal`() {
        // fe44819b7ec4c03b > Long.MAX_VALUE: um encoder com sinal produziria outro calldata
        original.payload.pHash.value shouldBe 0xfe44819b7ec4c03bUL
        RegistryTxBuilder.registerCapture(
            original.payload.sha256,
            original.payload.pHash,
            original.payload.deviceId,
            original.rawHex.hexToByteArray()
        ).substring(CALLDATA_PHASH_START, CALLDATA_PHASH_END) shouldBe
            "000000000000000000000000000000000000000000000000fe44819b7ec4c03b"
    }

    @Test
    fun `recusa assinatura que não tenha 64 bytes`() {
        shouldThrow<IllegalArgumentException> {
            RegistryTxBuilder.registerCapture(original.payload.sha256, original.payload.pHash, 1, ByteArray(63))
        }
    }

    @Test
    fun `recusa chave pública fora do formato não comprimido`() {
        shouldThrow<IllegalArgumentException> { RegistryTxBuilder.registerDevice(ByteArray(64)) }
    }

    private companion object {
        // "0x" + seletor (8) + sha256 (64) → segundo argumento
        const val CALLDATA_PHASH_START = 2 + 8 + 64
        const val CALLDATA_PHASH_END = CALLDATA_PHASH_START + 64
    }
}
