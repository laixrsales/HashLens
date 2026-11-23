package br.ufv.hashlens.attester.audit

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Erro do nó RPC (JSON-RPC `error`) ou resposta fora do formato. */
class RpcException(message: String) : IOException(message)

/**
 * Cliente JSON-RPC mínimo da auditoria: `eth_call` e `eth_getLogs`.
 *
 * @param send envia o corpo e devolve a resposta; nos testes, um nó falso.
 */
class JsonRpc(private val send: (String) -> String) {
    constructor(url: String) : this(httpSender(url))

    fun ethCall(to: String, data: String): String {
        val call = JsonObject().apply {
            addProperty("to", to)
            addProperty("data", data)
        }
        return request(
            "eth_call",
            JsonArray().apply {
                add(call)
                add("latest")
            }
        ).asString
    }

    /** `data` de cada log de [address] com [topics] no bloco [block]. */
    fun getLogs(address: String, topics: List<String>, block: Long): List<String> {
        val filter = JsonObject().apply {
            addProperty("address", address)
            add("topics", JsonArray().apply { topics.forEach(::add) })
            addProperty("fromBlock", "0x" + block.toString(HEX_RADIX))
            addProperty("toBlock", "0x" + block.toString(HEX_RADIX))
        }
        return request("eth_getLogs", JsonArray().apply { add(filter) }).asJsonArray
            .map { it.asJsonObject["data"].asString }
    }

    private fun request(method: String, params: JsonArray): com.google.gson.JsonElement {
        val body = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", method)
            add("params", params)
        }
        val response = JsonParser.parseString(send(body.toString())).asJsonObject
        response["error"]?.let { throw RpcException("$method: $it") }
        return response["result"] ?: throw RpcException("$method sem result")
    }

    private companion object {
        const val HEX_RADIX = 16
        private val TIMEOUT: Duration = Duration.ofSeconds(20)

        fun httpSender(url: String): (String) -> String {
            // O Chainstack responde 403 ao HTTP/2 que o cliente do Java tenta por padrão
            val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT).build()
            return { body ->
                val request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != HTTP_OK) {
                    throw RpcException("HTTP ${response.statusCode()}: ${response.body().take(ERROR_BODY_CHARS)}")
                }
                response.body()
            }
        }

        const val HTTP_OK = 200
        const val ERROR_BODY_CHARS = 200
    }
}
