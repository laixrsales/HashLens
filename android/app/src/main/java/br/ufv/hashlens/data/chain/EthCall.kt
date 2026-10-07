package br.ufv.hashlens.data.chain

import java.io.IOException
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService

/** Transporte de `eth_call` (bloqueante). Separado do [RegistryReader] para os testes. */
fun interface EthCall {
    /**
     * Executa `eth_call` em [to] com o calldata [data] no bloco mais recente e devolve o retorno ABI em hex.
     *
     * @throws RegistryException.Network ou [RegistryException.Rpc].
     */
    fun call(to: String, data: String): String
}

/** [EthCall] via JSON-RPC HTTP com web3j, sem carteira (research R2). */
class Web3jEthCall(rpcUrl: String) : EthCall {
    private val web3j = Web3j.build(HttpService(rpcUrl))

    override fun call(to: String, data: String): String {
        val response = try {
            web3j.ethCall(Transaction.createEthCallTransaction(null, to, data), DefaultBlockParameterName.LATEST)
                .send()
        } catch (e: IOException) {
            throw RegistryException.Network(e)
        }
        response.error?.let { throw RegistryException.Rpc(it.code, it.message.orEmpty()) }
        return response.value.orEmpty()
    }
}
