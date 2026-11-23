package br.ufv.hashlens.data.attestation

import br.ufv.hashlens.domain.model.RevocationStatus
import io.kotest.matchers.shouldBe
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * 020/T048: lista de revogação do Google na verificação (research R12, FR-017), com um servidor
 * falso e o cache HTTP real do OkHttp em disco.
 */
class RevocationRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private val cacheDir: File = Files.createTempDirectory("revocation").toFile()
    private val http = OkHttpClient.Builder().cache(Cache(cacheDir, CACHE_BYTES)).build()
    private val repository =
        RevocationRepository(http, Dispatchers.Unconfined, server.url("/attestation/status").toString())

    @AfterEach
    fun tearDown() {
        runCatching { server.close() }
        cacheDir.deleteRecursively()
    }

    private fun list(cacheControl: String = "max-age=3600", vararg entries: Pair<String, String>) =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .addHeader("Cache-Control", cacheControl)
            .body(
                entries.joinToString(prefix = """{"entries":{""", postfix = "}}") { (serial, status) ->
                    """"$serial":{"status":"$status","reason":"KEY_COMPROMISE"}"""
                }
            )
            .build()

    @Test
    fun `serial da cadeia revogado depois do vínculo marca REVOKED_AFTER_BINDING`() = runTest {
        server.enqueue(list(entries = arrayOf(INTERMEDIATE to "REVOKED")))

        repository.status(listOf(LEAF, INTERMEDIATE, ROOT)) shouldBe RevocationStatus.REVOKED_AFTER_BINDING
    }

    @Test
    fun `serial suspenso também marca REVOKED_AFTER_BINDING`() = runTest {
        server.enqueue(list(entries = arrayOf(LEAF to "SUSPENDED")))

        repository.status(listOf(LEAF, INTERMEDIATE, ROOT)) shouldBe RevocationStatus.REVOKED_AFTER_BINDING
    }

    @Test
    fun `serial publicado em decimal também é reconhecido`() = runTest {
        server.enqueue(list(entries = arrayOf(BigInteger(INTERMEDIATE, 16).toString() to "REVOKED")))

        repository.status(listOf(LEAF, INTERMEDIATE, ROOT)) shouldBe RevocationStatus.REVOKED_AFTER_BINDING
    }

    @Test
    fun `serial com caixa diferente na lista é reconhecido`() = runTest {
        server.enqueue(list(entries = arrayOf(LEAF.uppercase() to "REVOKED")))

        repository.status(listOf(LEAF)) shouldBe RevocationStatus.REVOKED_AFTER_BINDING
    }

    @Test
    fun `serial ausente da lista é NOT_REVOKED`() = runTest {
        server.enqueue(list(entries = arrayOf("deadbeef" to "REVOKED")))

        repository.status(listOf(LEAF, INTERMEDIATE, ROOT)) shouldBe RevocationStatus.NOT_REVOKED
    }

    @Test
    fun `outro status na lista não conta como revogado`() = runTest {
        server.enqueue(list(entries = arrayOf(LEAF to "SOFTWARE_FLAW")))

        repository.status(listOf(LEAF)) shouldBe RevocationStatus.NOT_REVOKED
    }

    @Test
    fun `sem rede e sem cache é UNKNOWN`() = runTest {
        server.close()

        repository.status(listOf(LEAF)) shouldBe RevocationStatus.UNKNOWN
    }

    @Test
    fun `erro do servidor sem cache é UNKNOWN`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        repository.status(listOf(LEAF)) shouldBe RevocationStatus.UNKNOWN
    }

    @Test
    fun `resposta fora do formato é UNKNOWN`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"entries":[]}""").build())

        repository.status(listOf(LEAF)) shouldBe RevocationStatus.UNKNOWN
    }

    @Test
    fun `dentro do max-age a lista vem do cache, sem nova requisição`() = runTest {
        server.enqueue(list(entries = arrayOf(LEAF to "REVOKED")))

        repository.status(listOf(LEAF)) shouldBe RevocationStatus.REVOKED_AFTER_BINDING
        repository.status(listOf(INTERMEDIATE)) shouldBe RevocationStatus.NOT_REVOKED

        server.requestCount shouldBe 1
    }

    @Test
    fun `sem rede, a última lista guardada vale mesmo vencida`() = runTest {
        // Vencida já ao chegar: a próxima consulta tenta a rede, que caiu
        server.enqueue(list(cacheControl = "max-age=0", entries = arrayOf(LEAF to "REVOKED")))
        repository.status(listOf(LEAF)) shouldBe RevocationStatus.REVOKED_AFTER_BINDING

        server.close()

        repository.status(listOf(LEAF)) shouldBe RevocationStatus.REVOKED_AFTER_BINDING
        repository.status(listOf(INTERMEDIATE)) shouldBe RevocationStatus.NOT_REVOKED
    }

    @Test
    fun `erro do servidor com cache vencido usa a lista guardada`() = runTest {
        server.enqueue(list(cacheControl = "max-age=0", entries = arrayOf(LEAF to "REVOKED")))
        repository.status(listOf(LEAF))
        server.enqueue(MockResponse.Builder().code(500).build())

        repository.status(listOf(LEAF)) shouldBe RevocationStatus.REVOKED_AFTER_BINDING
    }

    private companion object {
        const val CACHE_BYTES = 1024L * 1024
        const val LEAF = "1f3a"
        const val INTERMEDIATE = "c35747a084470c3135aeefe2b8d40cd6"
        const val ROOT = "e8fa196314d2fa18"
    }
}
