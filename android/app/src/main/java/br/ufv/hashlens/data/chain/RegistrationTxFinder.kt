package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.di.IoDispatcher
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Transação que criou cada registro (US5: link para a transação). O contrato não guarda a transação
 * de um registro, só o registro; ela vem dos eventos `ImageRegistered`, buscados com `eth_getLogs`
 * desde o bloco do deploy ([ChainConfig.deployBlock]).
 */
class RegistrationTxFinder @Inject constructor(
    private val config: ChainConfig,
    private val rpc: ChainRpc,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * `txHash` de cada registro da árvore de [originalId], por id. Uma consulta só: o evento indexa o
     * `originalId`, que na própria original é o id dela.
     *
     * @throws RegistryException.Network ou [RegistryException.Rpc] (inclui RPC que recusa o intervalo
     *   de blocos).
     */
    suspend fun byOriginal(originalId: Long): Map<Long, String> = withContext(ioDispatcher) {
        val topics = listOf(ReceiptTracker.IMAGE_REGISTERED_TOPIC, null, null, uint256Topic(originalId))
        rpc.getLogs(config.registryAddress, topics, config.deployBlock)
            .filter {
                it.address.equals(config.registryAddress, ignoreCase = true) &&
                    it.topics.size >= 2 &&
                    it.topics[0].equals(ReceiptTracker.IMAGE_REGISTERED_TOPIC, ignoreCase = true)
            }
            .mapNotNull { log -> log.transactionHash?.let { recordId(log) to it } }
            .toMap()
    }

    private companion object {
        const val HEX_RADIX = 16
        const val TOPIC_HEX_LENGTH = 64

        fun uint256Topic(value: Long): String = "0x" + value.toString(HEX_RADIX).padStart(TOPIC_HEX_LENGTH, '0')

        /** Primeiro tópico indexado do evento: o id do registro. */
        fun recordId(log: RawLog): Long = BigInteger(log.topics[1].removePrefix("0x"), HEX_RADIX).longValueExact()
    }
}
