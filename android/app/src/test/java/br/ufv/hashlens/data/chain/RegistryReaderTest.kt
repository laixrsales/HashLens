package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.testing.ChainResponses
import br.ufv.hashlens.testing.SignatureVectors
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** T028: [RegistryReader] sobre as respostas capturadas e mapeamento de falhas para [RegistryException]. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistryReaderTest {
    private val config = ChainConfig(rpcUrl = "", registryAddress = ChainResponses.contract, reownProjectId = "")
    private val original = SignatureVectors.case("original")

    /** Responde com a resposta capturada para o mesmo calldata, como o nó faria. */
    private val captured = EthCall { to, data ->
        to shouldBe ChainResponses.contract
        ChainResponses.calls.first { it.calldata == data }.response
    }

    private fun reader(ethCall: EthCall = captured) = RegistryReader(config, ethCall, UnconfinedTestDispatcher())

    @Test
    fun `lê registros, aparelhos e índices`() = runTest {
        val reader = reader()

        reader.getRecord(2L)?.parentId shouldBe 1L
        reader.getRecord(999L).shouldBeNull()
        reader.getIdBySha256(original.payload.sha256) shouldBe 1L
        reader.getIdsByPHash(original.payload.pHash) shouldBe listOf(1L, 2L)
        reader.getChildren(1L) shouldBe listOf(2L)
        reader.getDevice(1)?.owner shouldBe ChainResponses.wallet
        reader.getDevice(99).shouldBeNull()
        reader.getDevicesOf(ChainResponses.wallet) shouldBe listOf(1)
        reader.totalRecords() shouldBe 2L
    }

    @Test
    fun `SHA-256 não registrado vira null`() = runTest {
        val unknown = EthCall { _, _ -> ChainResponses.response("getIdBySha256 inexistente") }

        reader(unknown).getIdBySha256(original.payload.sha256).shouldBeNull()
    }

    @Test
    fun `falha de rede sai como RegistryException Network`() = runTest {
        val offline = EthCall { _, _ -> throw RegistryException.Network(IOException("sem conexão")) }

        shouldThrow<RegistryException.Network> { reader(offline).getRecord(1L) }
    }

    @Test
    fun `erro do nó sai como RegistryException Rpc`() = runTest {
        val failing = EthCall { _, _ -> throw RegistryException.Rpc(-32_000, "execution reverted") }

        shouldThrow<RegistryException.Rpc> { reader(failing).totalRecords() }.code shouldBe -32_000
    }

    @Test
    fun `retorno que não decodifica sai como RegistryException InvalidResponse`() = runTest {
        val garbage = EthCall { _, _ -> "0x" }

        val error = shouldThrow<RegistryException.InvalidResponse> { reader(garbage).getRecord(1L) }
        error.cause.shouldBeInstanceOf<IllegalArgumentException>()
    }
}
