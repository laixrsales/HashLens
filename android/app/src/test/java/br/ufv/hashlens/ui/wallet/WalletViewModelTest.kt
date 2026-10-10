package br.ufv.hashlens.ui.wallet

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.ChainRpc
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.wallet.WalletAppDetector
import br.ufv.hashlens.data.wallet.WalletEvent
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.ui.components.WalletChipState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

/** T056: folha "Conectar carteira" a partir da sessão, da rede e do saldo (ux.md §4.3, FR-003). */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val walletState = MutableStateFlow<WalletState>(WalletState.Disconnected)
    private val walletEvents = MutableSharedFlow<WalletEvent>(extraBufferCapacity = 1)
    private val session = mockk<WalletSession>(relaxUnitFun = true) {
        every { state } returns walletState
        every { events } returns walletEvents
    }
    private val rpc = mockk<ChainRpc>()
    private var hasWalletApp = true
    private val detector = WalletAppDetector { hasWalletApp }
    private val config = ChainConfig(rpcUrl = "", registryAddress = "0x0", reownProjectId = "")

    @BeforeEach
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel() = WalletViewModel(session, rpc, detector, config, dispatcher)

    /** Coleta os efeitos de [viewModel] enquanto o teste roda. */
    private fun TestScope.effects(viewModel: WalletViewModel): List<WalletEffect> {
        val collected = mutableListOf<WalletEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { collected += it } }
        return collected
    }

    private fun chain(balanceWei: BigInteger, gasPriceWei: BigInteger = GAS_PRICE) {
        every { rpc.getBalance(ADDRESS) } returns balanceWei
        every { rpc.gasPrice() } returns gasPriceWei
    }

    @Test
    fun `sem carteira, registrar abre a folha inicial`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onRegisterPhoto()

        viewModel.ui.value.sheet shouldBe WalletSheetUiState.Initial
        viewModel.ui.value.chip shouldBe WalletChipState.Disconnected
    }

    @Test
    fun `conectar abre a lista de carteiras e mostra a espera`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.onRegisterPhoto()

        viewModel.onAction(WalletSheetAction.Connect)

        viewModel.ui.value.sheet shouldBe WalletSheetUiState.Connecting
        effects shouldBe listOf(WalletEffect.OpenWalletList)
    }

    @Test
    fun `sem app de carteira instalado explica antes de abrir a lista`() = runTest(dispatcher) {
        hasWalletApp = false
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.onRegisterPhoto()

        viewModel.onAction(WalletSheetAction.Connect)
        viewModel.ui.value.sheet shouldBe WalletSheetUiState.NotInstalled
        effects shouldBe emptyList()

        // "Escolher uma carteira": a lista do AppKit tem os links para instalar
        viewModel.onAction(WalletSheetAction.Connect)
        effects shouldBe listOf(WalletEffect.OpenWalletList)
    }

    @Test
    fun `recusa na carteira mostra conexão recusada`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onRegisterPhoto()
        viewModel.onAction(WalletSheetAction.Connect)

        walletEvents.emit(WalletEvent.ConnectionRejected)

        viewModel.ui.value.sheet shouldBe WalletSheetUiState.Rejected
    }

    @Test
    fun `conexão aprovada na Sepolia com saldo mostra a conta e continuar segue para a câmera`() = runTest(dispatcher) {
        chain(balanceWei = ENOUGH)
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.onRegisterPhoto()
        viewModel.onAction(WalletSheetAction.Connect)

        walletState.value = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)

        viewModel.ui.value.sheet shouldBe WalletSheetUiState.Connected(ADDRESS, BigDecimal("0.01"))
        viewModel.ui.value.chip shouldBe WalletChipState.Connected(ADDRESS)
        viewModel.onAction(WalletSheetAction.Continue)
        viewModel.ui.value.sheet shouldBe null
        effects shouldBe listOf(WalletEffect.OpenWalletList, WalletEffect.ProceedToCapture)
    }

    @Test
    fun `carteira em outra rede pede a troca para Sepolia`() = runTest(dispatcher) {
        walletState.value = WalletState.Connected(ADDRESS, chainId = 1)
        val viewModel = viewModel()

        viewModel.onRegisterPhoto()

        viewModel.ui.value.sheet shouldBe WalletSheetUiState.WrongNetwork(ADDRESS)
        viewModel.ui.value.chip shouldBe WalletChipState.WrongNetwork(ADDRESS)
        viewModel.onAction(WalletSheetAction.SwitchToSepolia)
        coVerify { session.switchToSepolia() }
    }

    @Test
    fun `saldo abaixo do custo típico de um registro avisa e leva ao faucet`() = runTest(dispatcher) {
        // Custo típico: 360 mil de gás × 1 gwei = 0,00036; saldo de 0,0001
        chain(balanceWei = BigInteger.valueOf(100_000_000_000_000))
        walletState.value = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onRegisterPhoto()

        viewModel.ui.value.sheet.shouldBeInstanceOf<WalletSheetUiState.LowBalance>().balance shouldBe
            BigDecimal("0.0001")
        viewModel.onAction(WalletSheetAction.OpenFaucet)
        effects shouldBe listOf(WalletEffect.OpenUrl(config.faucetUrl))
    }

    @Test
    fun `carteira pronta segue direto para a câmera, sem folha`() = runTest(dispatcher) {
        chain(balanceWei = ENOUGH)
        walletState.value = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)
        val viewModel = viewModel()
        val effects = effects(viewModel)

        viewModel.onRegisterPhoto()

        viewModel.ui.value.sheet shouldBe null
        effects shouldBe listOf(WalletEffect.ProceedToCapture)
    }

    @Test
    fun `sem rede para ler o saldo, a carteira conectada não bloqueia o registro`() = runTest(dispatcher) {
        // A checagem que vale é a estimativa com a transação real (T103)
        every { rpc.getBalance(ADDRESS) } throws RegistryException.Network(IOException("sem conexão"))
        every { rpc.gasPrice() } returns GAS_PRICE
        walletState.value = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)
        val viewModel = viewModel()

        viewModel.onOpenWallet()

        viewModel.ui.value.sheet shouldBe WalletSheetUiState.Connected(ADDRESS, balance = null)
    }

    @Test
    fun `fechar a folha esquece o pedido de registro`() = runTest(dispatcher) {
        chain(balanceWei = ENOUGH)
        val viewModel = viewModel()
        val effects = effects(viewModel)
        viewModel.onRegisterPhoto()
        viewModel.onAction(WalletSheetAction.Dismiss)

        walletState.value = WalletState.Connected(ADDRESS, ChainConfig.SEPOLIA_CHAIN_ID)

        viewModel.ui.value.sheet shouldBe null
        effects shouldBe emptyList()
    }

    private companion object {
        const val ADDRESS = "0x1234567890AbcdEF1234567890aBcdef12345678"
        val GAS_PRICE: BigInteger = BigInteger.valueOf(1_000_000_000)
        val ENOUGH: BigInteger = BigInteger.TEN.pow(16)
    }
}
