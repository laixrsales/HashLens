package br.ufv.hashlens.data.attester

import br.ufv.hashlens.domain.model.AttestationRetryException
import br.ufv.hashlens.domain.model.AttesterUnavailableException
import br.ufv.hashlens.domain.model.DeviceNotEligibleException
import br.ufv.hashlens.domain.model.NotEligibleReason
import com.google.gson.JsonParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** 020/T031: [AttesterClient] contra um servidor falso, com a tabela de `attester-api.md` §4. */
@OptIn(ExperimentalCoroutinesApi::class)
class AttesterClientTest {
    private val server = MockWebServer()
    private val http = OkHttpClient.Builder().readTimeout(Duration.ofMillis(500)).build()
    private lateinit var client: AttesterClient

    private val wallet = "0x1234567890AbcdEF1234567890aBcdef12345678"
    private val publicKey = byteArrayOf(0x04) + ByteArray(64) { it.toByte() }
    private val chain = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5))

    @BeforeEach
    fun start() {
        server.start()
        client = AttesterClient(server.url("/").toString(), http, UnconfinedTestDispatcher())
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun respond(code: Int, body: String) {
        server.enqueue(
            MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()
        )
    }

    @Test
    fun `200 devolve a autorizacao e envia so carteira, chave e cadeia`() = runTest {
        respond(
            200,
            """{"deadline": 1767225600, "attestationHash": "0x${"ab".repeat(
                32
            )}", "authorization": "0x${"cd".repeat(65)}"}"""
        )

        val authorization = client.authorize(wallet, publicKey, chain)

        authorization.deadline shouldBe 1_767_225_600L
        authorization.attestationHash.toList() shouldBe ByteArray(32) { 0xab.toByte() }.toList()
        authorization.signature.toList() shouldBe ByteArray(65) { 0xcd.toByte() }.toList()

        val request = server.takeRequest()
        request.method shouldBe "POST"
        request.url.encodedPath shouldBe "/v1/authorize"
        val json = JsonParser.parseString(request.body!!.utf8()).asJsonObject
        json.keySet() shouldBe setOf("wallet", "publicKey", "chain")
        json["wallet"].asString shouldBe wallet
        json["publicKey"].asString shouldBe "0x04" + (0 until 64).joinToString("") { "%02x".format(it) }
        json["chain"].asJsonArray.map { Base64.getDecoder().decode(it.asString).toList() } shouldBe
            chain.map { it.toList() }
    }

    @ParameterizedTest
    @CsvSource(
        "BOOT_NOT_VERIFIED, MODIFIED",
        "PATCH_TOO_OLD, OUTDATED",
        "NOT_HARDWARE, NO_SECURE_HARDWARE",
        "CHAIN_INVALID, NO_SECURE_HARDWARE",
        "KEY_PROPERTIES, NO_SECURE_HARDWARE",
        "APP_NOT_GENUINE, APP_NOT_GENUINE",
        "REVOKED, REVOKED"
    )
    fun `422 vira DeviceNotEligibleException com o motivo`(reason: String, expected: NotEligibleReason) = runTest {
        respond(422, """{"reason": "$reason"}""")

        shouldThrow<DeviceNotEligibleException> { client.authorize(wallet, publicKey, chain) }.reason shouldBe expected
    }

    @ParameterizedTest
    @CsvSource("CHALLENGE_INVALID", "KEY_MISMATCH")
    fun `422 de desafio ou chave pede nova tentativa`(reason: String) = runTest {
        respond(422, """{"reason": "$reason"}""")

        shouldThrow<AttestationRetryException> { client.authorize(wallet, publicKey, chain) }.reason shouldBe reason
    }

    @ParameterizedTest
    @CsvSource(
        "400, '{\"reason\": \"MALFORMED\"}'",
        "503, '{\"reason\": \"REVOCATION_UNAVAILABLE\"}'",
        "500, 'erro'",
        "422, '{\"reason\": \"MOTIVO_NOVO\"}'",
        "422, 'nao e json'",
        "200, '{\"deadline\": 1}'",
        "200, 'nao e json'"
    )
    fun `demais respostas viram AttesterUnavailableException`(code: Int, body: String) = runTest {
        respond(code, body)

        shouldThrow<AttesterUnavailableException> { client.authorize(wallet, publicKey, chain) }
    }

    @Test
    fun `timeout vira AttesterUnavailableException`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(2, TimeUnit.SECONDS).build())

        shouldThrow<AttesterUnavailableException> { client.authorize(wallet, publicKey, chain) }
    }

    @Test
    fun `sem conexao vira AttesterUnavailableException`() = runTest {
        val url = server.url("/").toString()
        server.close()

        shouldThrow<AttesterUnavailableException> {
            AttesterClient(url, http, UnconfinedTestDispatcher()).authorize(wallet, publicKey, chain)
        }
    }

    @Test
    fun `sem endereco configurado vira AttesterUnavailableException`() = runTest {
        shouldThrow<AttesterUnavailableException> {
            AttesterClient("", http, UnconfinedTestDispatcher()).authorize(wallet, publicKey, chain)
        }
    }
}
