package br.ufv.hashlens.data.chain

import java.io.IOException
import java.math.BigInteger
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction

/** Log de um recibo, como o nó devolve (hex com `0x`). */
data class RawLog(val address: String, val topics: List<String>, val data: String)

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
}

/** [ChainRpc] via JSON-RPC HTTP com web3j (research R2). */
class Web3jChainRpc(private val web3j: Web3j) : ChainRpc {
    override fun estimateGas(from: String, to: String, data: String): BigInteger =
        send(web3j.ethEstimateGas(Transaction.createEthCallTransaction(from, to, data))).amountUsed

    override fun gasPrice(): BigInteger = send(web3j.ethGasPrice()).gasPrice

    override fun getBalance(address: String): BigInteger =
        send(web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)).balance

    override fun getTransactionReceipt(txHash: String): RawReceipt? {
        val receipt = send(web3j.ethGetTransactionReceipt(txHash)).transactionReceipt.orElse(null) ?: return null
        return RawReceipt(
            success = receipt.isStatusOK,
            logs = receipt.logs.map { RawLog(it.address, it.topics, it.data) }
        )
    }

    private fun <T : Response<*>> send(request: Request<*, T>): T {
        val response = try {
            request.send()
        } catch (e: IOException) {
            throw RegistryException.Network(e)
        }
        response.error?.let { throw RegistryException.Rpc(it.code, it.message.orEmpty()) }
        return response
    }
}
