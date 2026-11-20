package br.ufv.hashlens.attester

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * 020/T029: lista de revogação de `android.googleapis.com/attestation/status` (formato da
 * documentação do Android), com cache pelo `Cache-Control`.
 */
class RevocationListTest {
    private var now = Instant.parse("2026-11-18T12:00:00Z")
    private var fetches = 0
    private var response: () -> RevocationList.Response = { ok(BODY, "max-age=3600") }

    private val list = RevocationList(fetch = {
        fetches++
        response()
    }, clock = { now })

    private fun ok(body: String, cacheControl: String?) = RevocationList.Response(200, body, cacheControl)

    @Test
    fun `le os seriais revogados e suspensos, em hex minusculo`() {
        list.revokedSerials() shouldBe setOf("2c8cdddfd5e03bfc", "c35747a084470c3135aeefe2b8d40cd6", "1a2b3c")
    }

    @Test
    fun `dentro do max-age usa o cache, sem buscar de novo`() {
        list.revokedSerials()
        now = now.plus(Duration.ofMinutes(59))
        list.revokedSerials()

        fetches shouldBe 1
    }

    @Test
    fun `depois do max-age busca de novo`() {
        list.revokedSerials()
        now = now.plus(Duration.ofMinutes(61))
        response = { ok("""{"entries": {"ff": {"status": "REVOKED"}}}""", "max-age=60") }

        list.revokedSerials() shouldBe setOf("ff")
        fetches shouldBe 2
    }

    @Test
    fun `sem Cache-Control, ou no-cache, busca a cada consulta`() {
        response = { ok(BODY, null) }
        list.revokedSerials()
        list.revokedSerials()
        fetches shouldBe 2

        response = { ok(BODY, "no-cache") }
        list.revokedSerials()
        fetches shouldBe 3
    }

    @Test
    fun `rede falha com cache vencido ha pouco, usa o cache`() {
        list.revokedSerials()
        now = now.plus(Duration.ofHours(2))
        response = { throw IOException("sem rede") }

        list.revokedSerials() shouldBe setOf("2c8cdddfd5e03bfc", "c35747a084470c3135aeefe2b8d40cd6", "1a2b3c")
    }

    @Test
    fun `rede falha sem cache, erro`() {
        response = { throw IOException("sem rede") }

        shouldThrow<RevocationUnavailableException> { list.revokedSerials() }
    }

    @Test
    fun `rede falha com cache vencido ha mais de 24 h, erro`() {
        list.revokedSerials()
        now = now.plus(Duration.ofHours(1)).plus(Duration.ofHours(24)).plusSeconds(1)
        response = { throw IOException("sem rede") }

        shouldThrow<RevocationUnavailableException> { list.revokedSerials() }
    }

    @Test
    fun `status diferente de 200 ou corpo invalido contam como falha`() {
        response = { RevocationList.Response(500, "", null) }
        shouldThrow<RevocationUnavailableException> { list.revokedSerials() }

        response = { ok("nao e json", "max-age=60") }
        shouldThrow<RevocationUnavailableException> { list.revokedSerials() }

        response = { ok("""{"sem": "entries"}""", "max-age=60") }
        shouldThrow<RevocationUnavailableException> { list.revokedSerials() }
    }

    private companion object {
        // Formato de https://developer.android.com/privacy-and-security/security-key-attestation
        val BODY = """
            {
              "entries": {
                "2c8cdddfd5e03bfc": {"status": "REVOKED", "expires": "2020-11-13", "reason": "KEY_COMPROMISE", "comment": "Key stored on unsecure system"},
                "c35747a084470c3135aeefe2b8d40cd6": {"status": "SUSPENDED", "reason": "SOFTWARE_FLAW", "comment": "Bug in keystore causes this key malfunction b/555555"},
                "1A2B3C": {"status": "REVOKED"}
              }
            }
        """.trimIndent()
    }
}
