package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.testing.ChainReceipts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * T050: recibos e eventos do ImageRegistry, com recibos reais de `chain/receipts.json`. A espera
 * roda em tempo virtual.
 */
class ReceiptTrackerTest {
    private val rpc = mockk<ChainRpc>()
    private val config = ChainConfig(rpcUrl = "", registryAddress = ChainReceipts.contract, reownProjectId = "")

    private fun TestScope.tracker() = ReceiptTracker(config, rpc, StandardTestDispatcher(testScheduler))

    private fun parse(name: String) = ReceiptTracker.parse(ChainReceipts.receipt(name), ChainReceipts.contract)

    @Test
    fun `registerDevice traz o deviceId do evento DeviceRegistered`() {
        parse("registerDevice") shouldBe TxReceipt(success = true, deviceId = 1)
    }

    @Test
    fun `registerCapture e registerEdit trazem o recordId do evento ImageRegistered`() {
        parse("registerCapture original") shouldBe TxReceipt(success = true, recordId = 1)
        parse("registerEdit edicao") shouldBe TxReceipt(success = true, recordId = 2)
    }

    @Test
    fun `transação revertida não traz ids`() {
        parse("registerCapture repetida") shouldBe TxReceipt(success = false)
    }

    @Test
    fun `ignora eventos emitidos por outro contrato`() {
        val raw = ChainReceipts.receipt("registerCapture original")

        ReceiptTracker.parse(raw, OTHER_CONTRACT).recordId.shouldBeNull()
    }

    @Test
    fun `endereço do contrato não depende de maiúsculas`() {
        val raw = ChainReceipts.receipt("registerCapture original")

        ReceiptTracker.parse(raw, ChainReceipts.contract.uppercase().replace("0X", "0x")).recordId shouldBe 1
    }

    @Test
    fun `receipt devolve null enquanto a transação não foi minerada`() = runTest {
        every { rpc.getTransactionReceipt(TX) } returns null

        tracker().receipt(TX).shouldBeNull()
    }

    @Test
    fun `await consulta até haver recibo e tolera falha de rede`() = runTest {
        every { rpc.getTransactionReceipt(TX) } returnsMany listOf(null, null) andThenThrows
            RegistryException.Network(IOException("sem conexão")) andThen ChainReceipts.receipt("registerDevice")

        tracker().await(TX) shouldBe TxReceipt(success = true, deviceId = 1)

        verify(exactly = 4) { rpc.getTransactionReceipt(TX) }
        currentTime shouldBe 3 * ReceiptTracker.POLL_INTERVAL.toMillis()
    }

    @Test
    fun `await sem recibo no prazo lança TimedOut`() = runTest {
        every { rpc.getTransactionReceipt(TX) } returns null

        shouldThrow<TransactionException.TimedOut> { tracker().await(TX, Duration.ofMinutes(1)) }
        currentTime shouldBe Duration.ofMinutes(1).toMillis()
    }

    @Test
    fun `erro do nó interrompe a espera`() = runTest {
        every { rpc.getTransactionReceipt(TX) } throws RegistryException.Rpc(-32602, "hash inválido")

        shouldThrow<RegistryException.Rpc> { tracker().await(TX) }
    }

    @Test
    fun `awaitDeviceRegistered devolve o deviceId`() = runTest {
        every { rpc.getTransactionReceipt(TX) } returns ChainReceipts.receipt("registerDevice")

        tracker().awaitDeviceRegistered(TX) shouldBe 1
    }

    @Test
    fun `awaitDeviceRegistered com transação revertida lança Reverted`() = runTest {
        every { rpc.getTransactionReceipt(TX) } returns ChainReceipts.receipt("registerCapture repetida")

        shouldThrow<TransactionException.Reverted> { tracker().awaitDeviceRegistered(TX) }
    }

    @Test
    fun `awaitDeviceRegistered sem o evento lança MissingEvent`() = runTest {
        // Sucesso, mas é o recibo de uma captura: não há DeviceRegistered
        every { rpc.getTransactionReceipt(TX) } returns ChainReceipts.receipt("registerCapture original")

        shouldThrow<TransactionException.MissingEvent> { tracker().awaitDeviceRegistered(TX) }
    }

    private companion object {
        const val TX = "0x5555555555555555555555555555555555555555555555555555555555555555"
        const val OTHER_CONTRACT = "0x00000000000000000000000000000000000000b2"
    }
}
