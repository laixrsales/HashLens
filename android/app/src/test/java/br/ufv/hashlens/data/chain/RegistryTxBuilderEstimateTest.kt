package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** T049: estimativa de gás com margem e checagem de saldo antes de abrir a carteira. */
class RegistryTxBuilderEstimateTest {
    private val rpc = mockk<ChainRpc>()
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")

    private fun chain(gas: Long, price: Long, balance: Long) {
        every { rpc.estimateGas(WALLET, CONTRACT, DATA) } returns BigInteger.valueOf(gas)
        every { rpc.gasPrice() } returns BigInteger.valueOf(price)
        every { rpc.getBalance(WALLET) } returns BigInteger.valueOf(balance)
    }

    @Test
    fun `aplica 20 por cento de margem sobre a estimativa do nó`() = runTest {
        chain(gas = 100_000, price = 2_000_000_000, balance = 1)

        val cost = RegistryTxBuilder(config, rpc, StandardTestDispatcher(testScheduler)).estimate(WALLET, DATA)

        cost.gasLimit shouldBe BigInteger.valueOf(120_000)
        cost.maxCost shouldBe BigInteger.valueOf(120_000L * 2_000_000_000)
    }

    @Test
    fun `saldo que cobre o custo máximo é suficiente`() = runTest {
        chain(gas = 100_000, price = 10, balance = 1_200_000)

        RegistryTxBuilder(config, rpc, StandardTestDispatcher(testScheduler))
            .estimate(WALLET, DATA).hasEnoughBalance shouldBe true
    }

    @Test
    fun `saldo abaixo do custo máximo é insuficiente`() = runTest {
        chain(gas = 100_000, price = 10, balance = 1_199_999)

        RegistryTxBuilder(config, rpc, StandardTestDispatcher(testScheduler))
            .estimate(WALLET, DATA).hasEnoughBalance shouldBe false
    }

    @Test
    fun `transação que reverteria propaga o erro do nó`() = runTest {
        every { rpc.estimateGas(WALLET, CONTRACT, DATA) } throws RegistryException.Rpc(3, "execution reverted")

        shouldThrow<RegistryException.Rpc> {
            RegistryTxBuilder(config, rpc, StandardTestDispatcher(testScheduler)).estimate(WALLET, DATA)
        }
    }

    private companion object {
        const val CONTRACT = "0xF2f42B34414936e456c6AC10c4844f456CAD65Ba"
        const val WALLET = "0x1234567890abcdef1234567890abcdef12345678"
        const val DATA = "0xdeadbeef"
    }
}
