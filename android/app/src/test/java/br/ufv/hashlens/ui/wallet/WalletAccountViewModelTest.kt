package br.ufv.hashlens.ui.wallet

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.ChainRpc
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T091: tela Carteira (ux.md §4.11): a conta conectada, a rede, o saldo de teste (com o aviso de
 * saldo baixo pelo custo típico de um registro, FR-003) e "Desconectar". Desconectada, por aqui ou
 * pelo app da carteira, a tela fecha; câmera e edição voltam a pedir a carteira (WalletViewModel).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletAccountViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val walletState =
        MutableStateFlow<WalletState>(WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID))
    private val session = mockk<WalletSession> {
        every { state } returns walletState
        coEvery { disconnect() } answers { walletState.value = WalletState.Disconnected }
        coEvery { switchToSepolia() } answers {
            walletState.value = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)
        }
    }
    private val rpc = mockk<ChainRpc> {
        every { getBalance(ADDRESS) } returns ENOUGH
        every { gasPrice() } returns GAS_PRICE
    }
    private val config = ChainConfig(rpcUrl = "", registryAddress = "0x0", reownProjectId = "")

    @BeforeEach
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel() = WalletAccountViewModel(session, rpc, config, dispatcher)

    private fun TestScope.effects(viewModel: WalletAccountViewModel): List<WalletAccountEffect> {
        val collected = mutableListOf<WalletAccountEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { collected += it } }
        return collected
    }

    @Test
    fun `conta na Sepolia com saldo`() = runTest(dispatcher) {
        viewModel().ui.value shouldBe WalletAccountUiState(ADDRESS, onSepolia = true, balance = BigDecimal("0.01"))
    }

    @Test
    fun `saldo abaixo do custo típico de um registro fica sinalizado`() = runTest(dispatcher) {
        every { rpc.getBalance(ADDRESS) } returns BigInteger.ONE

        val state = viewModel().ui.value.shouldNotBeNull()

        state.lowBalance shouldBe true
    }

    @Test
    fun `em outra rede pede a troca, e trocar atualiza a tela`() = runTest(dispatcher) {
        walletState.value = WalletState.Connected(ADDRESS, chainId = 1)
        val viewModel = viewModel()
        viewModel.ui.value.shouldNotBeNull().onSepolia shouldBe false

        viewModel.onAction(WalletAccountAction.SwitchToSepolia)

        coVerify(exactly = 1) { session.switchToSepolia() }
        viewModel.ui.value.shouldNotBeNull().onSepolia shouldBe true
    }

    @Test
    fun `troca de rede recusada na carteira mantém o pedido`() = runTest(dispatcher) {
        walletState.value = WalletState.Connected(ADDRESS, chainId = 1)
        coEvery { session.switchToSepolia() } throws WalletException.Rejected()
        val viewModel = viewModel()

        viewModel.onAction(WalletAccountAction.SwitchToSepolia)

        viewModel.ui.value.shouldNotBeNull().onSepolia shouldBe false
    }

    @Test
    fun `sem rede para ler o saldo, a tela diz que não conseguiu consultar`() = runTest(dispatcher) {
        every { rpc.getBalance(ADDRESS) } throws RegistryException.Network(IOException("sem conexão"))

        val state = viewModel().ui.value.shouldNotBeNull()

        state.balance shouldBe null
        state.balanceUnavailable shouldBe true
        state.lowBalance shouldBe false
    }

    @Test
    fun `desconectar encerra a sessão e fecha a tela`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(WalletAccountAction.Disconnect)

        coVerify(exactly = 1) { session.disconnect() }
        effects shouldContainExactly listOf(WalletAccountEffect.Close)
    }

    @Test
    fun `desconectada pelo app da carteira, a tela também fecha`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)

        walletState.value = WalletState.Disconnected

        effects shouldContainExactly listOf(WalletAccountEffect.Close)
    }

    @Test
    fun `copiar leva o endereço completo, e o faucet abre o link`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onAction(WalletAccountAction.CopyAddress)
        viewModel.onAction(WalletAccountAction.OpenFaucet)
        viewModel.onAction(WalletAccountAction.Back)

        effects shouldContainExactly listOf(
            WalletAccountEffect.CopyAddress(ADDRESS),
            WalletAccountEffect.OpenUrl(config.faucetUrl),
            WalletAccountEffect.Close
        )
    }

    private companion object {
        const val ADDRESS = "0x1234567890AbcdEF1234567890aBcdef12345678"
        val GAS_PRICE: BigInteger = BigInteger.valueOf(1_000_000_000)
        val ENOUGH: BigInteger = BigInteger.TEN.pow(16)
    }
}
