package br.ufv.hashlens.testing

import br.ufv.hashlens.data.chain.RawLog
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Base64

/**
 * Respostas reais de `eth_call` do ImageRegistry em `src/test/resources/chain/responses.json`,
 * capturadas com `cast` num anvil local por `tools/chain/capturar_respostas.sh`. Os registros 1 e
 * 2 são os casos "original" e "edicao" de [SignatureVectors], com assinaturas válidas; o aparelho 1
 * foi vinculado (v2, 020/T023) com a cadeia real de `attestation/real/SM_M526B-tee.json`.
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

    /** Horário do bloco do vínculo do aparelho 1 (`Device.registeredAt`). */
    val deviceTime: Instant get() = Instant.ofEpochSecond(root["timestamps"]["aparelho"].asLong())

    /** Bloco do vínculo do aparelho 1 (`Device.registeredBlock`). */
    val deviceBlock: Long get() = root["blocoAparelho"].asLong()

    /** Cadeia publicada no vínculo do aparelho 1 (DER, da folha para a raiz). */
    val deviceChain: List<ByteArray> by lazy { root["cadeiaAparelho"].map { Base64.getDecoder().decode(it.asText()) } }

    /** `keccak256(abi.encode(deviceChain))`, calculado pelo `cast`. */
    val attestationHash: String get() = root["attestationHash"].asText()

    /** Resposta de `eth_getLogs` capturada para o filtro [name]. */
    fun logs(name: String): List<RawLog> =
        root["logs"].single { it["nome"].asText() == name }["resposta"].map { log ->
            RawLog(
                address = log["address"].asText(),
                topics = log["topics"].map { it.asText() },
                data = log["data"].asText()
            )
        }
}
