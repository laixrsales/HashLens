package br.ufv.hashlens.testing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Respostas reais de `eth_call` do ImageRegistry em `src/test/resources/chain/responses.json`,
 * capturadas com `cast` num anvil local por `tools/chain/capturar_respostas.sh`. Os registros 1 e
 * 2 são os casos "original" e "edicao" de [SignatureVectors], com assinaturas válidas.
 */
object ChainResponses {
    data class Call(
        val name: String,
        val function: String,
        val argument: String,
        val calldata: String,
        val response: String
    )

    private val root: JsonNode by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/chain/responses.json")) {
            "recurso ausente: chain/responses.json"
        }
        stream.use { ObjectMapper().readTree(it) }
    }

    val calls: List<Call> by lazy {
        root["respostas"].map {
            Call(
                name = it["nome"].asText(),
                function = it["funcao"].asText(),
                argument = it["argumento"].asText(),
                calldata = it["calldata"].asText(),
                response = it["resposta"].asText()
            )
        }
    }

    fun call(name: String): Call = calls.single { it.name == name }

    fun response(name: String): String = call(name).response

    val contract: String get() = root["contrato"].asText()

    val wallet: String get() = root["carteira"].asText()

    val captureTime: Instant get() = Instant.ofEpochSecond(root["timestamps"]["captura"].asLong())

    val editTime: Instant get() = Instant.ofEpochSecond(root["timestamps"]["edicao"].asLong())
}
