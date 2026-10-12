package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Transação de cada registro de uma árvore (US5, critério 3: link para a transação). O contrato não
 * guarda a transação de um registro, só o registro; ela vem dos eventos `ImageRegistered`, filtrados
 * pelo `originalId` indexado (na original, o contrato grava o próprio id).
 */
class RegistrationTxFinderTest {
    private val rpc = mockk<ChainRpc>()
    private val config = ChainConfig(
        rpcUrl = "",
        registryAddress = CONTRACT,
        reownProjectId = "",
        deployBlock = DEPLOY_BLOCK
    )

    private fun TestScope.finder() = RegistrationTxFinder(config, rpc, StandardTestDispatcher(testScheduler))

    private fun topic(value: Long) = "0x" + value.toString(16).padStart(64, '0')

    private fun log(id: Long, txHash: String?, address: String = CONTRACT, event: String = IMAGE_REGISTERED) = RawLog(
        address = address,
        topics = listOf(event, topic(id), topic(0xAB), topic(ORIGINAL)),
        data = "0x",
        transactionHash = txHash
    )

    @Test
    fun `busca os eventos ImageRegistered da original desde o bloco do deploy`() = runTest {
        every { rpc.getLogs(any(), any(), any()) } returns emptyList()

        finder().byOriginal(ORIGINAL)

        verify {
            rpc.getLogs(CONTRACT, listOf(IMAGE_REGISTERED, null, null, topic(ORIGINAL)), DEPLOY_BLOCK)
        }
    }

    @Test
    fun `devolve a transação de cada registro da árvore pelo id do evento`() = runTest {
        every { rpc.getLogs(any(), any(), any()) } returns listOf(log(7, TX_A), log(9, TX_B), log(300, TX_C))

        finder().byOriginal(ORIGINAL) shouldContainExactly mapOf(7L to TX_A, 9L to TX_B, 300L to TX_C)
    }

    @Test
    fun `ignora logs de outro contrato, de outro evento ou sem transação`() = runTest {
        every { rpc.getLogs(any(), any(), any()) } returns listOf(
            log(7, TX_A),
            log(8, TX_B, address = OTHER_CONTRACT),
            log(9, TX_C, event = topic(1)),
            log(10, txHash = null)
        )

        finder().byOriginal(ORIGINAL) shouldContainExactly mapOf(7L to TX_A)
    }

    @Test
    fun `endereço do contrato não depende de maiúsculas`() = runTest {
        every { rpc.getLogs(any(), any(), any()) } returns
            listOf(log(7, TX_A, address = CONTRACT.uppercase().replace("0X", "0x")))

        finder().byOriginal(ORIGINAL) shouldContainExactly mapOf(7L to TX_A)
    }

    @Test
    fun `falha do nó sai como RegistryException`() = runTest {
        every { rpc.getLogs(any(), any(), any()) } throws RegistryException.Network(IOException("offline"))

        shouldThrow<RegistryException.Network> { finder().byOriginal(ORIGINAL) }
    }

    private companion object {
        const val CONTRACT = "0xf2f42b34414936e456c6ac10c4844f456cad65ba"
        const val OTHER_CONTRACT = "0x0000000000000000000000000000000000000001"
        const val DEPLOY_BLOCK = 11_858_572L
        const val ORIGINAL = 7L
        val IMAGE_REGISTERED = ReceiptTracker.IMAGE_REGISTERED_TOPIC
        const val TX_A = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val TX_B = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val TX_C = "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    }
}
