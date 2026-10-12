package br.ufv.hashlens.data.chain

import java.io.IOException
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.exceptions.ClientConnectionException

// Envio de requisições JSON-RPC com web3j, comum a Web3jEthCall e Web3jChainRpc

/**
 * Envia a requisição e devolve a resposta sem erro JSON-RPC.
 *
 * @throws RegistryException.Network sem conexão, tempo esgotado ou resposta HTTP de erro.
 * @throws RegistryException.Rpc se o nó responder com erro JSON-RPC (inclui chamada revertida).
 */
internal fun <T : Response<*>> Request<*, T>.sendChecked(): T {
    val response = sendOverNetwork()
    response.error?.let { throw RegistryException.Rpc(it.code, it.message.orEmpty()) }
    return response
}

private fun <T : Response<*>> Request<*, T>.sendOverNetwork(): T = try {
    send()
} catch (e: IOException) {
    throw RegistryException.Network(e)
} catch (e: ClientConnectionException) {
    // Resposta HTTP de erro (ex.: 403 do plano do provedor de RPC, 429): no web3j não é IOException
    throw RegistryException.Network(IOException(e.message, e))
}
