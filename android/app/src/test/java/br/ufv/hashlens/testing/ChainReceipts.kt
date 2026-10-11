package br.ufv.hashlens.testing

import br.ufv.hashlens.data.chain.RawLog
import br.ufv.hashlens.data.chain.RawReceipt
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Recibos reais das transações de escrita do ImageRegistry em `src/test/resources/chain/receipts.json`,
 * capturados num anvil local por `tools/chain/capturar_respostas.sh` (mesmos registros de
 * [ChainResponses]: aparelho 1, registro 1 "original", registro 2 "edicao").
 */
object ChainReceipts {
    private val root: JsonNode by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/chain/receipts.json")) {
            "recurso ausente: chain/receipts.json"
        }
        stream.use { ObjectMapper().readTree(it) }
    }

    /** Endereço do contrato que emitiu os eventos. */
    val contract: String by lazy { root["contrato"].asText() }

    fun receipt(name: String): RawReceipt {
        val node = root["recibos"].single { it["nome"].asText() == name }
        return RawReceipt(
            success = node["status"].asText() == "0x1",
            logs = node["logs"].map { log ->
                RawLog(
                    address = log["address"].asText(),
                    topics = log["topics"].map { it.asText() },
                    data = log["data"].asText()
                )
            }
        )
    }
}
