package br.ufv.hashlens.data.attestation

import br.ufv.hashlens.domain.model.RevocationStatus
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.IOException
import java.math.BigInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Lista de revogação do Google na verificação (020 research R12, FR-017):
 * `GET android.googleapis.com/attestation/status`, com o cache HTTP do [http] respeitando o
 * `Cache-Control` da resposta.
 *
 * Se a rede falhar, usa a última lista guardada no cache, mesmo vencida; sem ela, a situação fica
 * [RevocationStatus.UNKNOWN]. A revogação nunca muda a classificação, só acrescenta um aviso.
 *
 * @param url endereço da lista; nos testes, o do servidor falso.
 */
class RevocationRepository(
    private val http: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val url: String = STATUS_URL
) {
    private class Parsed(val receivedAt: Long, val serials: Set<String>)

    /** A última lista lida, para não reler o JSON a cada registro da mesma resposta. */
    @Volatile
    private var parsed: Parsed? = null

    /**
     * @param serials seriais da cadeia do aparelho em hex minúsculo, da folha para a raiz
     *   (`AttestationVerdict.Accepted.certSerials`).
     */
    suspend fun status(serials: List<String>): RevocationStatus = withContext(ioDispatcher) {
        val revoked = revokedSerials() ?: return@withContext RevocationStatus.UNKNOWN
        val forms = serials.flatMap(::serialForms)
        if (forms.any { it in revoked }) RevocationStatus.REVOKED_AFTER_BINDING else RevocationStatus.NOT_REVOKED
    }

    /** Seriais revogados ou suspensos; `null` se não há lista da rede nem do cache. */
    private fun revokedSerials(): Set<String>? = fetch(Request.Builder().url(url).build())
        ?: fetch(Request.Builder().url(url).cacheControl(CacheControl.FORCE_CACHE).build())

    /** Lista de uma resposta 2xx; `null` em erro de rede, de status ou de JSON. */
    private fun fetch(request: Request): Set<String>? = try {
        http.newCall(request).execute().use { response -> if (response.isSuccessful) parse(response) else null }
    } catch (_: IOException) {
        null
    } catch (_: JsonParseException) {
        null
    } catch (_: IllegalStateException) {
        // JSON válido, mas sem `entries` ou com um campo que não é texto
        null
    } catch (_: ClassCastException) {
        // JSON válido, mas `entries` ou uma entrada não é objeto
        null
    }

    private fun parse(response: Response): Set<String> {
        parsed?.takeIf { it.receivedAt == response.receivedResponseAtMillis }?.let { return it.serials }
        val entries = JsonParser.parseString(response.body.string()).asJsonObject.getAsJsonObject("entries")
            ?: error("resposta sem entries")
        val serials = entries.entrySet()
            .filter { (_, entry) -> entry.asJsonObject["status"]?.asString in BLOCKING_STATUSES }
            .map { (serial, _) -> serial.lowercase() }
            .toSet()
        parsed = Parsed(response.receivedResponseAtMillis, serials)
        return serials
    }

    private companion object {
        const val STATUS_URL = "https://android.googleapis.com/attestation/status"
        val BLOCKING_STATUSES = setOf("REVOKED", "SUSPENDED")
        const val HEX_RADIX = 16

        /**
         * A lista do Google diz usar hex, mas parte das chaves parece decimal; como na política do
         * atestador, o serial conta nas duas formas para não deixar passar um revogado.
         */
        fun serialForms(hex: String): List<String> {
            val normalized = hex.lowercase()
            val decimal = normalized.toBigIntegerOrNull(HEX_RADIX)?.let(BigInteger::toString)
            return listOfNotNull(normalized, decimal)
        }
    }
}
