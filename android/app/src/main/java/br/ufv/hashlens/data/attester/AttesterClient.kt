package br.ufv.hashlens.data.attester

import br.ufv.hashlens.domain.model.AttestationRetryException
import br.ufv.hashlens.domain.model.AttesterUnavailableException
import br.ufv.hashlens.domain.model.DeviceAuthorization
import br.ufv.hashlens.domain.model.DeviceNotEligibleException
import br.ufv.hashlens.domain.model.NotEligibleReason
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Cliente do `POST /v1/authorize` do atestador (`contracts/attester-api.md`). Envia só a carteira,
 * a chave pública e a cadeia (FR-002) e traduz a resposta pela tabela do §4.
 *
 * @param baseUrl `ChainConfig.attesterUrl`; no debug, `http://localhost:8080` com `adb reverse`.
 */
class AttesterClient(
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * @throws DeviceNotEligibleException recusa definitiva (422).
     * @throws AttestationRetryException desafio ou chave recusados (422 `CHALLENGE_INVALID`, `KEY_MISMATCH`).
     * @throws AttesterUnavailableException qualquer outra resposta, timeout ou falta de conexão.
     */
    suspend fun authorize(wallet: String, publicKey: ByteArray, chain: List<ByteArray>): DeviceAuthorization =
        withContext(ioDispatcher) {
            val url = baseUrl.trimEnd('/').plus("/v1/authorize").toHttpUrlOrNull()
                ?: throw AttesterUnavailableException(IllegalStateException("attester.url não configurado"))
            val body = JsonObject().apply {
                addProperty("wallet", wallet)
                addProperty("publicKey", "0x" + publicKey.toHex())
                add("chain", JsonArray().apply { chain.forEach { add(Base64.getEncoder().encodeToString(it)) } })
            }
            val request = Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build()
            val (code, text) = try {
                http.newCall(request).execute().use { it.code to it.body.string() }
            } catch (e: IOException) {
                throw AttesterUnavailableException(e)
            }
            when (code) {
                HTTP_OK -> parseAuthorization(text)
                HTTP_UNPROCESSABLE -> throw rejection(text)
                else -> throw AttesterUnavailableException(IllegalStateException("HTTP $code"))
            }
        }

    private fun parseAuthorization(text: String): DeviceAuthorization = try {
        val json = JsonParser.parseString(text).asJsonObject
        DeviceAuthorization(
            deadline = json["deadline"].asLong,
            attestationHash = json["attestationHash"].asString.hexToBytes(),
            signature = json["authorization"].asString.hexToBytes()
        ).also {
            require(it.attestationHash.size == HASH_SIZE && it.signature.size == SIGNATURE_SIZE)
        }
    } catch (
        // Gson e a conversão hex lançam vários tipos para corpo malformado
        @Suppress("TooGenericExceptionCaught") e: RuntimeException
    ) {
        throw AttesterUnavailableException(e)
    }

    private fun rejection(text: String): Exception {
        val reason = runCatching { JsonParser.parseString(text).asJsonObject["reason"].asString }.getOrNull()
        return when (reason) {
            "BOOT_NOT_VERIFIED" -> DeviceNotEligibleException(NotEligibleReason.MODIFIED)
            "PATCH_TOO_OLD" -> DeviceNotEligibleException(NotEligibleReason.OUTDATED)
            "NOT_HARDWARE", "CHAIN_INVALID", "KEY_PROPERTIES" ->
                DeviceNotEligibleException(NotEligibleReason.NO_SECURE_HARDWARE)
            "APP_NOT_GENUINE" -> DeviceNotEligibleException(NotEligibleReason.APP_NOT_GENUINE)
            "REVOKED" -> DeviceNotEligibleException(NotEligibleReason.REVOKED)
            "CHALLENGE_INVALID", "KEY_MISMATCH" -> AttestationRetryException(reason)
            else -> AttesterUnavailableException(IllegalStateException("motivo desconhecido: $reason"))
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val digits = removePrefix("0x")
        require(digits.length % HEX_DIGITS_PER_BYTE == 0)
        return digits.chunked(HEX_DIGITS_PER_BYTE).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val HTTP_OK = 200
        const val HTTP_UNPROCESSABLE = 422
        const val HASH_SIZE = 32
        const val SIGNATURE_SIZE = 65
        const val HEX_DIGITS_PER_BYTE = 2
        const val HEX_RADIX = 16
    }
}
