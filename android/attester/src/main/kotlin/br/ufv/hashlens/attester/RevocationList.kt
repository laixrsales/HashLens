package br.ufv.hashlens.attester

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/** A lista de revogação não pôde ser obtida e não há cache utilizável (resposta 503). */
class RevocationUnavailableException(cause: Throwable) : Exception("lista de revogação indisponível", cause)

/**
 * Lista de certificados revogados ou suspensos do Google (`android.googleapis.com/attestation/status`,
 * regra 2 da policy). Guarda em memória a última resposta pelo `max-age` do `Cache-Control`; se a
 * busca falhar com o cache vencido, ainda usa o cache por até [staleTolerance], e depois disso
 * lança [RevocationUnavailableException].
 *
 * @param fetch faz o GET; nos testes, uma resposta fixa.
 * @param clock instante atual; nos testes, um relógio controlado.
 */
class RevocationList(
    private val fetch: () -> Response = ::httpGet,
    private val clock: () -> Instant = Instant::now,
    private val staleTolerance: Duration = DEFAULT_STALE_TOLERANCE
) {
    /** Resposta HTTP: só o que a lista usa. */
    class Response(val status: Int, val body: String, val cacheControl: String?)

    private class Cached(val serials: Set<String>, val freshUntil: Instant)

    @Volatile
    private var cached: Cached? = null

    /** Seriais (hex minúsculo) com status `REVOKED` ou `SUSPENDED`. */
    @Synchronized
    fun revokedSerials(): Set<String> {
        val now = clock()
        cached?.takeIf { now.isBefore(it.freshUntil) }?.let { return it.serials }
        return try {
            val response = fetch()
            check(response.status == HTTP_OK) { "status ${response.status}" }
            val serials = parse(response.body)
            cached = Cached(serials, now.plus(maxAge(response.cacheControl)))
            serials
        } catch (
            // Rede, status, JSON: qualquer falha da busca cai no cache ou vira indisponível
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            val stale = cached?.takeIf { !now.isAfter(it.freshUntil.plus(staleTolerance)) }
            stale?.serials ?: throw RevocationUnavailableException(e)
        }
    }

    private fun parse(body: String): Set<String> {
        val entries = JsonParser.parseString(body).asJsonObject.getAsJsonObject("entries")
            ?: error("resposta sem entries")
        return entries.entrySet()
            .filter { (_, entry) -> entry.asJsonObject["status"]?.asString in BLOCKING_STATUSES }
            .map { (serial, _) -> serial.lowercase() }
            .toSet()
    }

    private fun maxAge(cacheControl: String?): Duration {
        val directives = cacheControl.orEmpty().split(",").map { it.trim().lowercase() }
        if (directives.any { it == "no-cache" || it == "no-store" }) return Duration.ZERO
        val seconds = directives.firstOrNull { it.startsWith("max-age=") }?.substringAfter("=")?.toLongOrNull()
        return Duration.ofSeconds(seconds?.coerceAtLeast(0) ?: 0)
    }

    private companion object {
        const val HTTP_OK = 200
        val DEFAULT_STALE_TOLERANCE: Duration = Duration.ofHours(24)
        const val STATUS_URL = "https://android.googleapis.com/attestation/status"
        val BLOCKING_STATUSES = setOf("REVOKED", "SUSPENDED")
        private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

        fun httpGet(): Response {
            val request = HttpRequest.newBuilder(URI.create(STATUS_URL)).timeout(Duration.ofSeconds(15)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return Response(
                response.statusCode(),
                response.body(),
                response.headers().firstValue("cache-control").orElse(null)
            )
        }
    }
}
