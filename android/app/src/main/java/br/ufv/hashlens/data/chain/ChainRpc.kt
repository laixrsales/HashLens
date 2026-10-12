package br.ufv.hashlens.data.chain

import java.math.BigInteger
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthLog

/**
 * Log de um recibo ou de `eth_getLogs`, como o nó devolve (hex com `0x`).
 *
 * @property transactionHash transação que emitiu o log; vem em `eth_getLogs`.
 */
data class RawLog(val address: String, val topics: List<String>, val data: String, val transactionHash: String? = null)

/** Recibo de `eth_getTransactionReceipt`: `status` 1 (sucesso) ou 0 (revertida) e os logs. */
data class RawReceipt(val success: Boolean, val logs: List<RawLog>)

/**
 * JSON-RPC usado no envio e no acompanhamento de transações (bloqueante). Separado do
 * [RegistryTxBuilder] e do [ReceiptTracker] para os testes.
 *
 * Toda falha sai como [RegistryException.Network] ou [RegistryException.Rpc].
 */
interface ChainRpc {
    /** `eth_estimateGas`; um revert sai como [RegistryException.Rpc]. */
    fun estimateGas(from: String, to: String, data: String): BigInteger

    /** `eth_gasPrice`, em wei. */
    fun gasPrice(): BigInteger

    /** `eth_getBalance` no bloco mais recente, em wei. */
    fun getBalance(address: String): BigInteger

    /** `eth_getTransactionReceipt`; `null` enquanto a transação não foi minerada. */
    fun getTransactionReceipt(txHash: String): RawReceipt?

    /**
     * `eth_getLogs` de [address] de [fromBlock] até o bloco mais recente. [topics] segue a posição
     * dos tópicos; `null` aceita qualquer valor naquela posição.
     */
    fun getLogs(address: String, topics: List<String?>, fromBlock: Long): List<RawLog>
}

/** [ChainRpc] via JSON-RPC HTTP com web3j (research R2). */
class Web3jChainRpc(private val web3j: Web3j) : ChainRpc {
    override fun estimateGas(from: String, to: String, data: String): BigInteger =
        web3j.ethEstimateGas(Transaction.createEthCallTransaction(from, to, data)).sendChecked().amountUsed

    override fun gasPrice(): BigInteger = web3j.ethGasPrice().sendChecked().gasPrice

    override fun getBalance(address: String): BigInteger =
        web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).sendChecked().balance

    override fun getTransactionReceipt(txHash: String): RawReceipt? {
        val receipt =
            web3j.ethGetTransactionReceipt(txHash).sendChecked().transactionReceipt.orElse(null) ?: return null
        return RawReceipt(
            success = receipt.isStatusOK,
            logs = receipt.logs.map { RawLog(it.address, it.topics, it.data) }
        )
    }

    override fun getLogs(address: String, topics: List<String?>, fromBlock: Long): List<RawLog> {
        val filter = EthFilter(
            DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
            DefaultBlockParameterName.LATEST,
            address
        )
        topics.forEach { if (it == null) filter.addNullTopic() else filter.addSingleTopic(it) }
        return web3j.ethGetLogs(filter).sendChecked().logs
            .filterIsInstance<EthLog.LogObject>()
            .map { RawLog(it.address, it.topics, it.data, it.transactionHash) }
    }
}
