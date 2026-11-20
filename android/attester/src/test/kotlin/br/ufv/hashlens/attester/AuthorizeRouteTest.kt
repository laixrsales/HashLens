package br.ufv.hashlens.attester

import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.attestation.AttestationHash
import br.ufv.hashlens.attestation.AttestationPolicy
import br.ufv.hashlens.attestation.RejectReason
import br.ufv.hashlens.attestation.fixtures.TestChains
import br.ufv.hashlens.attestation.fixtures.TestChains.Params
import com.android.keyattestation.verifier.SecurityLevel
import com.android.keyattestation.verifier.VerifiedBootState
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.util.Base64
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** 020/T030: `POST /v1/authorize` e `GET /v1/health` (`contracts/attester-api.md`). */
class AuthorizeRouteTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val contract = "0xfBa7aeDa5e979F88336b35e81A1780b4655BAe6E"
    private val wallet = "0x1234567890AbcdEF1234567890aBcdef12345678"
    private val config = AttesterConfig(
        privateKey = TEST_KEY,
        chainId = 11_155_111,
        contract = contract,
        allowedCertDigests = setOf(TestChains.GENUINE_DIGEST)
    )
    private val signer = DeviceAuthorizationSigner(config.privateKey, config.chainId, config.contract)
    private val logs = mutableListOf<String>()
    private var revoked: () -> Set<String> = { emptySet() }

    private fun challenge(issuedAt: Instant = now.minusSeconds(30), wallet: String = this.wallet) =
        AttestationChallenge(config.chainId, contract, wallet, issuedAt).encode()

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            attesterModule(
                AttesterDependencies(
                    config = config,
                    signer = signer,
                    policy = AttestationPolicy(trustAnchors = { TestChains.anchors }, revokedSerials = { revoked() }),
                    clock = { now },
                    log = { logs += it }
                )
            )
        }
        block()
    }

    private fun body(
        generated: TestChains.Generated,
        wallet: String = this.wallet,
        publicKey: ByteArray = generated.publicKey
    ): String = JsonObject().apply {
        addProperty("wallet", wallet)
        addProperty("publicKey", "0x" + hex(publicKey))
        add(
            "chain",
            com.google.gson.JsonArray().apply {
                generated.chain.forEach { add(Base64.getEncoder().encodeToString(it)) }
            }
        )
    }.toString()

    private suspend fun ApplicationTestBuilder.authorize(json: String): HttpResponse = client.post("/v1/authorize") {
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    private suspend fun HttpResponse.json(): JsonObject = JsonParser.parseString(bodyAsText()).asJsonObject

    // ───────────────────────────── 200 ──────────────────────────────

    @Test
    fun `aceita e devolve prazo de 15 min, hash da cadeia e a autorizacao assinada`() = app {
        val generated = TestChains.generate(Params(challenge = challenge()))

        val response = authorize(body(generated))

        response.status shouldBe HttpStatusCode.OK
        val json = response.json()
        val deadline = now.plus(Duration.ofMinutes(15)).epochSecond
        json["deadline"].asLong shouldBe deadline
        json["attestationHash"].asString shouldBe "0x" + hex(AttestationHash.of(generated.chain))
        json["authorization"].asString shouldBe
            "0x" + hex(signer.sign(wallet, generated.publicKey, generated.chain, deadline))
    }

    // ───────────────────────────── 422 ──────────────────────────────

    @Test
    fun `recusa com o motivo da politica`() = app {
        val cases = mapOf(
            RejectReason.CHAIN_INVALID to Params(challenge = challenge(), leafSignedByStranger = true),
            RejectReason.NOT_HARDWARE to Params(
                challenge = challenge(),
                attestationSecurityLevel = SecurityLevel.SOFTWARE
            ),
            RejectReason.BOOT_NOT_VERIFIED to Params(challenge = challenge(), bootState = VerifiedBootState.UNVERIFIED),
            RejectReason.PATCH_TOO_OLD to Params(challenge = challenge(), osPatchLevel = YearMonth.of(2020, 1)),
            RejectReason.APP_NOT_GENUINE to Params(challenge = challenge(), signatureDigest = TestChains.OTHER_DIGEST),
            RejectReason.KEY_PROPERTIES to Params(challenge = challenge(), digests = setOf(TestChains.DIGEST_NONE)),
            RejectReason.CHALLENGE_INVALID to
                Params(challenge = challenge(issuedAt = now.minus(Duration.ofMinutes(16))))
        )
        for ((reason, params) in cases) {
            val response = authorize(body(TestChains.generate(params)))
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.json()["reason"].asString shouldBe reason.name
        }
    }

    @Test
    fun `recusa REVOKED quando um serial da cadeia esta na lista`() = app {
        val generated = TestChains.generate(Params(challenge = challenge()))
        revoked = { setOf(generated.serials[1]) }

        val response = authorize(body(generated))

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.json()["reason"].asString shouldBe "REVOKED"
    }

    @Test
    fun `recusa KEY_MISMATCH quando a chave da requisicao nao e a da folha`() = app {
        val generated = TestChains.generate(Params(challenge = challenge()))
        val other = TestChains.generate(Params(challenge = challenge())).publicKey

        val response = authorize(body(generated, publicKey = other))

        response.json()["reason"].asString shouldBe "KEY_MISMATCH"
    }

    @Test
    fun `desafio de outra carteira, CHALLENGE_INVALID`() = app {
        val generated = TestChains.generate(
            Params(challenge = challenge(wallet = "0x00000000000000000000000000000000000000aa"))
        )

        authorize(body(generated)).json()["reason"].asString shouldBe "CHALLENGE_INVALID"
    }

    // ───────────────────────────── 400 ──────────────────────────────

    @ParameterizedTest
    @ValueSource(
        strings = [
            "nao e json",
            "[]",
            """{"wallet": "0x1234567890AbcdEF1234567890aBcdef12345678", "publicKey": "0x04"}""",
            """{"wallet": "0x123", "publicKey": "0x04ab", "chain": ["AAAA", "AAAA"]}"""
        ]
    )
    fun `JSON invalido ou incompleto, MALFORMED`(json: String) = app {
        val response = authorize(json)
        response.status shouldBe HttpStatusCode.BadRequest
        response.json()["reason"].asString shouldBe "MALFORMED"
    }

    @Test
    fun `endereco, chave ou base64 invalidos, MALFORMED`() = app {
        val generated = TestChains.generate(Params(challenge = challenge()))
        val valid = JsonParser.parseString(body(generated)).asJsonObject

        val variants = listOf(
            valid.deepCopy().apply { addProperty("wallet", "0x1234") },
            valid.deepCopy().apply { addProperty("wallet", "nao-hex-0000000000000000000000000000000000") },
            valid.deepCopy().apply { addProperty("publicKey", "0x" + "ab".repeat(65)) }, // sem prefixo 04
            valid.deepCopy().apply { addProperty("publicKey", "0x04" + "ab".repeat(10)) },
            valid.deepCopy().apply { addProperty("publicKey", "0x04zz" + "ab".repeat(63)) },
            valid.deepCopy().apply {
                add(
                    "chain",
                    com.google.gson.JsonArray().apply {
                        add("%%%")
                        add("AAAA")
                    }
                )
            },
            valid.deepCopy().apply { addProperty("chain", "nao e lista") }
        )
        for (variant in variants) {
            val response = authorize(variant.toString())
            response.status shouldBe HttpStatusCode.BadRequest
            response.json()["reason"].asString shouldBe "MALFORMED"
        }
    }

    @Test
    fun `campos alem de wallet, publicKey e chain sao recusados`() = app {
        val generated = TestChains.generate(Params(challenge = challenge()))
        val extra = JsonParser.parseString(body(generated)).asJsonObject.apply { addProperty("imei", "123") }

        authorize(extra.toString()).status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `cadeia com menos de 2 ou mais de 8 certificados, MALFORMED`() = app {
        val generated = TestChains.generate(Params(challenge = challenge()))
        val one = JsonParser.parseString(body(generated)).asJsonObject.apply {
            add(
                "chain",
                com.google.gson.JsonArray().apply {
                    add(Base64.getEncoder().encodeToString(generated.chain[0]))
                }
            )
        }
        val nine = JsonParser.parseString(body(generated)).asJsonObject.apply {
            add("chain", com.google.gson.JsonArray().apply { repeat(9) { add("AAAA") } })
        }

        authorize(one.toString()).status shouldBe HttpStatusCode.BadRequest
        authorize(nine.toString()).status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `corpo acima de 32 KB, MALFORMED`() = app {
        val big = """{"wallet": "$wallet", "publicKey": "0x04", "chain": ["${"A".repeat(33 * 1024)}"]}"""

        val response = authorize(big)

        response.status shouldBe HttpStatusCode.BadRequest
        response.json()["reason"].asString shouldBe "MALFORMED"
    }

    // ───────────────────────────── 503 ──────────────────────────────

    @Test
    fun `lista de revogacao indisponivel, REVOCATION_UNAVAILABLE`() = app {
        revoked = { throw RevocationUnavailableException(java.io.IOException("sem rede")) }

        val response = authorize(body(TestChains.generate(Params(challenge = challenge()))))

        response.status shouldBe HttpStatusCode.ServiceUnavailable
        response.json()["reason"].asString shouldBe "REVOCATION_UNAVAILABLE"
    }

    @Test
    fun `cadeia invalida nao depende da lista de revogacao`() = app {
        revoked = { throw RevocationUnavailableException(java.io.IOException("sem rede")) }

        val response =
            authorize(body(TestChains.generate(Params(challenge = challenge(), leafSignedByStranger = true))))

        response.json()["reason"].asString shouldBe "CHAIN_INVALID"
    }

    // ───────────────────────────── health e log ─────────────────────

    @Test
    fun `health informa rede, contrato e atestador`() = app {
        val response = client.get("/v1/health")

        response.status shouldBe HttpStatusCode.OK
        val json = response.json()
        json["chainId"].asLong shouldBe 11_155_111
        json["contract"].asString shouldBe contract.lowercase()
        json["attester"].asString shouldBe signer.address
    }

    @Test
    fun `log tem status, motivo e carteira abreviada, sem cadeia nem chave`() = app {
        val accepted = TestChains.generate(Params(challenge = challenge()))
        val rejected = TestChains.generate(Params(challenge = challenge(), bootState = VerifiedBootState.UNVERIFIED))

        authorize(body(accepted))
        authorize(body(rejected))
        authorize("nao e json")

        logs.size shouldBe 3
        logs[0] shouldContain "200"
        logs[1] shouldContain "422"
        logs[1] shouldContain "BOOT_NOT_VERIFIED"
        logs[2] shouldContain "MALFORMED"
        logs[0] shouldContain "0x1234…5678"
        val secrets = (accepted.chain + rejected.chain).map { Base64.getEncoder().encodeToString(it).take(40) } +
            listOf(hex(accepted.publicKey).take(40), hex(rejected.publicKey).take(40), wallet.lowercase(), wallet)
        logs.forEach { line -> secrets.forEach { line shouldNotContain it } }
        logs.filter { it.contains(TEST_KEY.removePrefix("0x").take(16)) }.shouldBeEmpty()
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        /** Chave do atestador só de teste (a do vetor EIP-712). */
        const val TEST_KEY = "0x5ced8e422ac212abb5fb6540ee60873cd437259e2c5fecfdfe4c140710f5299d"
    }
}
