package br.ufv.hashlens.data.chain

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Test

/** 020: `eth_getLogs` vai para o nó com histórico; o resto continua no nó principal. */
class LogsSplitChainRpcTest {
    private val main = mockk<ChainRpc>()
    private val logs = mockk<ChainRpc>()
    private val rpc = LogsSplitChainRpc(main, logs)

    @Test
    fun `eventos vêm do nó com histórico`() {
        val log = RawLog("0xabc", listOf("0x01"), "0x")
        every { logs.getLogs("0xabc", listOf("0x01"), 10, 10) } returns listOf(log)

        rpc.getLogs("0xabc", listOf("0x01"), 10, 10) shouldBe listOf(log)

        verify(exactly = 0) { main.getLogs(any(), any(), any(), any()) }
    }

    @Test
    fun `recibos, saldo e gás continuam no nó principal`() {
        every { main.getTransactionReceipt("0xtx") } returns null
        every { main.getBalance("0xabc") } returns BigInteger.TEN
        every { main.gasPrice() } returns BigInteger.ONE

        rpc.getTransactionReceipt("0xtx") shouldBe null
        rpc.getBalance("0xabc") shouldBe BigInteger.TEN
        rpc.gasPrice() shouldBe BigInteger.ONE

        verify(exactly = 0) { logs.getTransactionReceipt(any()) }
    }
}
