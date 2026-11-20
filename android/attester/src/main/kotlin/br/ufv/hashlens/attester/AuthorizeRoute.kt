package br.ufv.hashlens.attester

import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.attestation.AttestationHash
import br.ufv.hashlens.attestation.AttestationVerdict
import br.ufv.hashlens.attestation.ChallengeWindow
import br.ufv.hashlens.attestation.ExpectedAttestation
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import java.time.Duration
import java.util.Base64
import kotlinx.io.readByteArray

/**
 * `POST /v1/authorize` e `GET /v1/health` (`contracts/attester-api.md`). O atestador não guarda
 * nada da requisição: valida a cadeia pela [AttestationPolicy] (com `referenceTime = agora` e a
 * janela do atestador) e, se aceita, assina a autorização com prazo de 15 min (FR-004).
 */
fun Application.attesterModule(deps: AttesterDependencies) {
    routing {
        get("/v1/health") {
            val body = JsonObject().apply {
                addProperty("chainId", deps.config.chainId)
                addProperty("contract", AttestationChallenge.normalizeAddress(deps.config.contract))
                addProperty("attester", deps.signer.address)
            }
            call.respondText(body.toString(), ContentType.Application.Json)
        }
        post("/v1/authorize") {
            val outcome = authorize(call, deps)
            deps.log("authorize ${outcome.status.value} ${outcome.reason ?: "OK"} ${outcome.wallet ?: "-"}")
            call.respondText(outcome.body.toString(), ContentType.Application.Json, outcome.status)
        }
    }
}

/** Resultado de uma requisição, com o que vai para o log. */
private class Outcome(val status: HttpStatusCode, val body: JsonObject, val reason: String?, val wallet: String?)

/** Requisição já validada no formato (§1). */
private class AuthorizeRequest(val wallet: String, val publicKey: ByteArray, val chain: List<ByteArray>)

private const val MAX_BODY_BYTES = 32 * 1024
private const val MIN_CHAIN = 2
private const val MAX_CHAIN = 8
private const val PUBLIC_KEY_SIZE = 65
private const val UNCOMPRESSED_PREFIX: Byte = 0x04
private const val WALLET_PREFIX_CHARS = 6
private const val WALLET_SUFFIX_CHARS = 4
private const val HEX_DIGITS_PER_BYTE = 2
private const val HEX_RADIX = 16
private val AUTHORIZATION_VALIDITY: Duration = Duration.ofMinutes(15)
private val ALLOWED_FIELDS = setOf("wallet", "publicKey", "chain")
private val HEX = Regex("[0-9a-fA-F]*")

private suspend fun authorize(call: RoutingCall, deps: AttesterDependencies): Outcome {
    val request = readRequest(call) ?: return rejection(HttpStatusCode.BadRequest, "MALFORMED", wallet = null)
    val wallet = abbreviate(request.wallet)
    val now = deps.clock()
    val expected = ExpectedAttestation(
        chainId = deps.config.chainId,
        contract = deps.config.contract,
        wallet = request.wallet,
        publicKey = request.publicKey,
        allowedCertDigests = deps.config.allowedCertDigests,
        referenceTime = now,
        window = ChallengeWindow.ATTESTER
    )
    val verdict = runCatching { deps.policy.evaluate(request.chain, expected) }
        .recover { if (it is RevocationUnavailableException) null else throw it }
        .getOrThrow()
    return when (verdict) {
        null -> rejection(HttpStatusCode.ServiceUnavailable, "REVOCATION_UNAVAILABLE", wallet)
        is AttestationVerdict.Rejected -> rejection(HttpStatusCode.UnprocessableEntity, verdict.reason.name, wallet)
        is AttestationVerdict.Accepted -> {
            val deadline = now.plus(AUTHORIZATION_VALIDITY).epochSecond
            val signature = deps.signer.sign(request.wallet, request.publicKey, request.chain, deadline)
            val body = JsonObject().apply {
                addProperty("deadline", deadline)
                addProperty("attestationHash", "0x" + hex(AttestationHash.of(request.chain)))
                addProperty("authorization", "0x" + hex(signature))
            }
            Outcome(HttpStatusCode.OK, body, reason = null, wallet = wallet)
        }
    }
}

private fun rejection(status: HttpStatusCode, reason: String, wallet: String?) =
    Outcome(status, JsonObject().apply { addProperty("reason", reason) }, reason, wallet)

/** Lê e valida o corpo; `null` para qualquer formato fora da §1 (400 MALFORMED). */
private suspend fun readRequest(call: RoutingCall): AuthorizeRequest? {
    val declared = call.request.contentLength() ?: 0
    val bytes = if (declared > MAX_BODY_BYTES) {
        null
    } else {
        call.receiveChannel().readRemaining(MAX_BODY_BYTES + 1L).readByteArray().takeIf { it.size <= MAX_BODY_BYTES }
    }
    return bytes?.let { runCatching { parse(String(it, Charsets.UTF_8)) }.getOrNull() }
}

private fun parse(text: String): AuthorizeRequest? {
    val json = JsonParser.parseString(text).asJsonObject
    val fields = json.keySet()
    val wallet = json["wallet"]?.asString?.let(AttestationChallenge::normalizeAddress)
    val publicKey = json["publicKey"]?.asString?.let(::hexBytes)
    val chain = json["chain"]?.asJsonArray?.map { Base64.getDecoder().decode(it.asString) }
    val valid = fields == ALLOWED_FIELDS &&
        wallet != null &&
        publicKey != null &&
        publicKey.size == PUBLIC_KEY_SIZE &&
        publicKey[0] == UNCOMPRESSED_PREFIX &&
        chain != null &&
        chain.size in MIN_CHAIN..MAX_CHAIN
    return if (valid) AuthorizeRequest(wallet!!, publicKey!!, chain!!) else null
}

private fun hexBytes(value: String): ByteArray? {
    val digits = value.removePrefix("0x")
    if (digits.length % 2 != 0 || !HEX.matches(digits)) return null
    return digits.chunked(HEX_DIGITS_PER_BYTE).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
}

private fun abbreviate(wallet: String): String =
    wallet.take(WALLET_PREFIX_CHARS) + "…" + wallet.takeLast(WALLET_SUFFIX_CHARS)

private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
