package br.ufv.hashlens.data.chain

import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction

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
class Web3jEthCall(private val web3j: Web3j) : EthCall {
    override fun call(to: String, data: String): String {
        val response =
            web3j.ethCall(Transaction.createEthCallTransaction(null, to, data), DefaultBlockParameterName.LATEST)
                .sendChecked()
        return response.value.orEmpty()
    }
}
