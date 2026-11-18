package br.ufv.hashlens.attestation

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

/** 020/T018: desafio de 95 bytes de `contracts/attestation-policy.md` §2. */
class AttestationChallengeTest {
    private val contract = "0xfBa7aeDa5e979F88336b35e81A1780b4655BAe6E"
    private val wallet = "0x1234567890AbcdEF1234567890aBcdef12345678"
    private val issuedAt = Instant.ofEpochSecond(1_767_225_600)
    private val challenge = AttestationChallenge(11_155_111, contract, wallet, issuedAt)

    @Test
    fun `tem 95 bytes, campos na ordem e inteiros em big-endian`() {
        val bytes = challenge.encode()

        bytes.size shouldBe 95
        String(bytes.copyOfRange(0, 15), Charsets.US_ASCII) shouldBe "HASHLENS-ATT-v1"
        hex(bytes.copyOfRange(15, 47)) shouldBe "00".repeat(29) + "aa36a7" // 11155111
        hex(bytes.copyOfRange(47, 67)) shouldBe "fba7aeda5e979f88336b35e81a1780b4655bae6e"
        hex(bytes.copyOfRange(67, 87)) shouldBe "1234567890abcdef1234567890abcdef12345678"
        hex(bytes.copyOfRange(87, 95)) shouldBe "000000006955b900" // 1767225600
    }

    @Test
    fun `le de volta o que codificou, com enderecos normalizados`() {
        val decoded = AttestationChallenge.decode(challenge.encode())!!

        decoded shouldBe challenge
        decoded.contract shouldBe contract.lowercase()
        decoded.wallet shouldBe wallet.lowercase()
        decoded.issuedAt shouldBe issuedAt
    }

    @Test
    fun `endereco sem 0x ou com outra caixa resulta no mesmo desafio`() {
        val other =
            AttestationChallenge(11_155_111, contract.removePrefix("0x").uppercase(), wallet.lowercase(), issuedAt)
        other shouldBe challenge
        other.encode().toList() shouldBe challenge.encode().toList()
    }

    @Test
    fun `nao le bytes com tamanho ou dominio diferentes`() {
        val bytes = challenge.encode()
        AttestationChallenge.decode(bytes.copyOf(94)).shouldBeNull()
        AttestationChallenge.decode(bytes + 0).shouldBeNull()
        AttestationChallenge.decode(ByteArray(0)).shouldBeNull()
        AttestationChallenge.decode(bytes.copyOf().also { it[14] = '2'.code.toByte() }).shouldBeNull()
    }

    @Test
    fun `janela do atestador, menos 15 min e mais 2 min, inclusive nas bordas`() {
        val window = ChallengeWindow.ATTESTER
        val at = { offset: Duration -> AttestationChallenge(11_155_111, contract, wallet, issuedAt.plus(offset)) }

        // referenceTime − 15 min ≤ issuedAt ≤ referenceTime + 2 min
        at(Duration.ofMinutes(-15)).isWithin(window, issuedAt) shouldBe true
        at(Duration.ofMinutes(-15).minusSeconds(1)).isWithin(window, issuedAt) shouldBe false
        at(Duration.ofMinutes(2)).isWithin(window, issuedAt) shouldBe true
        at(Duration.ofMinutes(2).plusSeconds(1)).isWithin(window, issuedAt) shouldBe false
        at(Duration.ZERO).isWithin(window, issuedAt) shouldBe true
    }

    @Test
    fun `janela da verificacao, menos 30 min e mais 2 min, inclusive nas bordas`() {
        val window = ChallengeWindow.VERIFICATION
        val at = { offset: Duration -> AttestationChallenge(11_155_111, contract, wallet, issuedAt.plus(offset)) }

        at(Duration.ofMinutes(-30)).isWithin(window, issuedAt) shouldBe true
        at(Duration.ofMinutes(-30).minusSeconds(1)).isWithin(window, issuedAt) shouldBe false
        at(Duration.ofMinutes(2)).isWithin(window, issuedAt) shouldBe true
        at(Duration.ofMinutes(2).plusSeconds(1)).isWithin(window, issuedAt) shouldBe false
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
