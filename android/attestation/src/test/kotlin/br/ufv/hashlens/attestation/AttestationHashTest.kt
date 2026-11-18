package br.ufv.hashlens.attestation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 020/T019: `attestationHash = keccak256(abi.encode(bytes[]))`, igual ao contrato. Os valores
 * esperados vêm de `cast keccak $(cast abi-encode "f(bytes[])" "[...]")` (cast 1.8.4).
 */
class AttestationHashTest {
    @Test
    fun `um certificado`() {
        hex(AttestationHash.of(listOf(bytes("3082010a0201")))) shouldBe
            "da9d5338bd7443aa80d5c880a3f33e0765297643df1361b354d74451aa3aba06"
    }

    @Test
    fun `tres certificados, o mesmo hash do vetor EIP-712`() {
        val chain = listOf(bytes("3082010a0201"), bytes("30820204a003020102"), bytes("308201e2"))
        hex(AttestationHash.of(chain)) shouldBe "59b252464a076c3e44a538e290b635269af7132d0f0401113a2c5000646352fd"
    }

    @Test
    fun `cinco certificados, com elemento vazio e elementos maiores que 32 bytes`() {
        val chain = listOf(
            bytes("01"),
            ByteArray(0),
            bytes("ab".repeat(40)),
            bytes("00ff"),
            bytes("cd".repeat(70))
        )
        hex(AttestationHash.of(chain)) shouldBe "eabef790bfe1fa0056a346390df5e048b412f6b06160f309190f0fb72aa6fad9"
    }

    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
