package br.ufv.hashlens.testing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Calldata de referência das funções de escrita em `src/test/resources/chain/calldata.json`,
 * gerado com `cast calldata` por `tools/chain/gerar_calldata.sh` a partir de [SignatureVectors].
 */
object CalldataVectors {
    private val root: JsonNode by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/chain/calldata.json")) {
            "recurso ausente: chain/calldata.json"
        }
        stream.use { ObjectMapper().readTree(it) }
    }

    fun calldata(name: String): String = root["calldata"].single { it["nome"].asText() == name }["calldata"].asText()
}
