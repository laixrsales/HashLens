package br.ufv.hashlens.attester

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * 020/T065: a chave do atestador vem só do ambiente (FR-019), e um valor malformado é recusado na
 * leitura do ambiente sem aparecer na mensagem de erro, que vai para o terminal.
 */
class AttesterConfigTest {
    private val validKey = "0x" + "11".repeat(32)
    private fun env(privateKey: String) = mapOf(
        "ATTESTER_PRIVATE_KEY" to privateKey,
        "ATTESTER_CHAIN_ID" to "11155111",
        "ATTESTER_CONTRACT" to "0xEd920f008400E817601a9Afa51d86dD975A1e6D3",
        "ATTESTER_ALLOWED_CERT_DIGESTS" to "AA:22"
    )

    @Test
    fun `chave com ou sem 0x e aceita`() {
        AttesterConfig.fromEnv(env(validKey)).privateKey shouldBe validKey
        AttesterConfig.fromEnv(env(validKey.removePrefix("0x"))).privateKey shouldBe validKey.removePrefix("0x")
    }

    @Test
    fun `chave malformada e recusada sem expor o valor`() {
        val malformed = listOf(
            "0x" + "zz".repeat(32), // não hexadecimal
            "0x" + "11".repeat(31), // curta
            validKey + "1", // um dígito a mais (erro comum ao colar)
            "0x" + "00".repeat(32) // zero: não é uma chave secp256k1
        )
        malformed.forEach { key ->
            val error = shouldThrow<IllegalArgumentException> { AttesterConfig.fromEnv(env(key)) }
            error.message shouldBe "ATTESTER_PRIVATE_KEY inválida (64 dígitos hexadecimais, com ou sem 0x)"
            error.message.orEmpty() shouldNotContain key.removePrefix("0x")
        }
    }
}
