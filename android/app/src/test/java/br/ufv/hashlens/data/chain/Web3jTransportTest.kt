package br.ufv.hashlens.data.chain

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import org.junit.jupiter.api.Test
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthCall as EthCallResponse
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.protocol.exceptions.ClientConnectionException

/**
 * Transportes JSON-RPC com web3j ([Web3jEthCall], [Web3jChainRpc]): toda falha do nó sai como
 * [RegistryException], nunca como exceção do web3j. Uma resposta HTTP de erro (ex.: 403 do plano do
 * provedor de RPC) chega como [ClientConnectionException], que não é `IOException`, e derrubava o
 * app ao abrir o Histórico (busca de eventos recusada pelo plano gratuito).
 */
class Web3jTransportTest {
    private val web3j = mockk<Web3j>()

    private fun <T : org.web3j.protocol.core.Response<*>> failing(error: Exception): Request<*, T> =
        mockk { every { send() } throws error }

    @Test
    fun `resposta HTTP de erro em eth_getLogs sai como falha de rede`() {
        every { web3j.ethGetLogs(any()) } returns
            failing<EthLog>(ClientConnectionException("Invalid response received: 403"))

        shouldThrow<RegistryException.Network> { Web3jChainRpc(web3j).getLogs(CONTRACT, listOf(TOPIC), fromBlock = 1) }
    }

    @Test
    fun `resposta HTTP de erro em eth_call sai como falha de rede`() {
        every { web3j.ethCall(any(), any()) } returns
            failing<EthCallResponse>(ClientConnectionException("Invalid response received: 429"))

        shouldThrow<RegistryException.Network> { Web3jEthCall(web3j).call(CONTRACT, "0x") }
    }

    @Test
    fun `sem conexão continua saindo como falha de rede`() {
        every { web3j.ethGetLogs(any()) } returns failing<EthLog>(IOException("sem conexão"))

        shouldThrow<RegistryException.Network> { Web3jChainRpc(web3j).getLogs(CONTRACT, listOf(TOPIC), fromBlock = 1) }
    }

    private companion object {
        const val CONTRACT = "0xf2f42b34414936e456c6ac10c4844f456cad65ba"
        val TOPIC = ReceiptTracker.IMAGE_REGISTERED_TOPIC
    }
}
