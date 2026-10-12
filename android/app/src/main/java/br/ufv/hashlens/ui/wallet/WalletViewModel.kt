package br.ufv.hashlens.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.ChainRpc
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.wallet.WalletAppDetector
import br.ufv.hashlens.data.wallet.WalletEvent
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.di.IoDispatcher
import br.ufv.hashlens.ui.common.SepoliaEthFormat
import br.ufv.hashlens.ui.components.WalletChipState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @property sheet folha "Conectar carteira" aberta, ou `null` com ela fechada.
 */
data class WalletUiState(
    val chip: WalletChipState = WalletChipState.Disconnected,
    val sheet: WalletSheetUiState? = null
)

/** O que a tela que hospeda a folha faz fora dela. */
sealed interface WalletEffect {
    /** Abre a lista de carteiras do AppKit. */
    data object OpenWalletList : WalletEffect

    data class OpenUrl(val url: String) : WalletEffect

    /** Carteira pronta: segue para onde a pessoa ia ([gate]). */
    data class Proceed(val gate: WalletGate) : WalletEffect
}

/**
 * Guarda de sessão (T091): o que só abre com a carteira conectada na Sepolia. Sem ela, a folha
 * "Conectar carteira" aparece e o destino segue depois de conectar. A verificação nunca passa por
 * aqui (FR-019).
 */
sealed interface WalletGate {
    /** "Registrar foto": a câmera. */
    data object Capture : WalletGate

    /** "Editar foto" no acervo: o Editor com a foto [localId] como pai. */
    data class Edit(val localId: Long) : WalletGate

    /** "Importar para editar" no Início. */
    data object ImportForEdit : WalletGate
}

/**
 * Carteira vista pelo Início (T056, ux.md §4.3): o chip do topo e a folha "Conectar carteira", com
 * conexão, rede, saldo e faucet.
 *
 * O saldo é comparado com o custo típico de um registro só para avisar cedo (FR-003); quem bloqueia
 * de fato é a estimativa com a transação real, antes de abrir a carteira (T103). Sem rede para ler
 * o saldo, a folha não bloqueia.
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val session: WalletSession,
    private val rpc: ChainRpc,
    private val detector: WalletAppDetector,
    private val config: ChainConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val _ui = MutableStateFlow(WalletUiState())
    val ui: StateFlow<WalletUiState> = _ui.asStateFlow()

    private val _effects = Channel<WalletEffect>(Channel.BUFFERED)
    val effects: Flow<WalletEffect> = _effects.receiveAsFlow()

    /** Destino que pediu a carteira: com ela pronta, segue para ele. */
    private var pending: WalletGate? = null

    init {
        viewModelScope.launch {
            session.state.collect { state ->
                _ui.update { it.copy(chip = state.toChip()) }
                // Aprovação, troca de conta ou de rede com a folha aberta: recalcula o que ela mostra
                if (_ui.value.sheet != null) refreshSheet()
            }
        }
        viewModelScope.launch {
            session.events.collect { event ->
                if (event == WalletEvent.ConnectionRejected && _ui.value.sheet == WalletSheetUiState.Connecting) {
                    showSheet(WalletSheetUiState.Rejected)
                }
            }
        }
    }

    /** Segue direto para [gate] se a carteira estiver pronta; senão, abre a folha. */
    fun requireWallet(gate: WalletGate) {
        pending = gate
        viewModelScope.launch {
            val sheet = sheetFor(session.state.value)
            if (sheet is WalletSheetUiState.Connected) {
                pending = null
                _effects.send(WalletEffect.Proceed(gate))
            } else {
                showSheet(sheet)
            }
        }
    }

    /** Toque no chip da carteira. */
    fun onOpenWallet() {
        pending = null
        viewModelScope.launch { showSheet(sheetFor(session.state.value)) }
    }

    fun onAction(action: WalletSheetAction) {
        when (action) {
            WalletSheetAction.Connect -> connect()
            WalletSheetAction.Cancel -> showSheet(WalletSheetUiState.Initial)
            WalletSheetAction.Continue -> {
                hideSheet()
                pending?.let { send(WalletEffect.Proceed(it)) }
                pending = null
            }
            WalletSheetAction.SwitchToSepolia -> viewModelScope.launch {
                // Recusa ou rede desconhecida na carteira: a folha segue pedindo a troca
                try {
                    session.switchToSepolia()
                } catch (_: WalletException) {
                }
                refreshSheet()
            }
            WalletSheetAction.OpenFaucet -> send(WalletEffect.OpenUrl(config.faucetUrl))
            WalletSheetAction.RefreshBalance -> viewModelScope.launch { refreshSheet() }
            WalletSheetAction.Dismiss -> {
                hideSheet()
                pending = null
            }
        }
    }

    private fun connect() {
        // Sem carteira instalada, explica primeiro; da segunda vez, a lista do AppKit oferece instalar uma
        if (_ui.value.sheet == WalletSheetUiState.Initial && !detector.hasWalletApp()) {
            showSheet(WalletSheetUiState.NotInstalled)
            return
        }
        showSheet(WalletSheetUiState.Connecting)
        send(WalletEffect.OpenWalletList)
    }

    private suspend fun refreshSheet() {
        val current = _ui.value.sheet ?: return
        val state = session.state.value
        // Ainda esperando a carteira, ou a recusa na tela: só muda quando a conta aparecer
        val waiting = current == WalletSheetUiState.Connecting || current == WalletSheetUiState.Rejected
        if (state is WalletState.Disconnected && waiting) return
        showSheet(sheetFor(state))
    }

    private suspend fun sheetFor(state: WalletState): WalletSheetUiState = when (state) {
        WalletState.Disconnected -> WalletSheetUiState.Initial
        is WalletState.Connected ->
            if (state.isSepolia) balanceSheet(state.address) else WalletSheetUiState.WrongNetwork(state.address)
    }

    private suspend fun balanceSheet(address: String): WalletSheetUiState {
        val reading = withContext(ioDispatcher) {
            try {
                rpc.getBalance(address) to rpc.gasPrice()
            } catch (_: RegistryException) {
                null
            }
        }
        val (balance, gasPrice) = reading ?: return WalletSheetUiState.Connected(address, balance = null)
        val balanceEth = SepoliaEthFormat.fromWei(balance).stripTrailingZeros()
        return if (RegistrationCost.isLow(balance, gasPrice)) {
            WalletSheetUiState.LowBalance(address, balanceEth)
        } else {
            WalletSheetUiState.Connected(address, balanceEth)
        }
    }

    private fun showSheet(sheet: WalletSheetUiState) = _ui.update { it.copy(sheet = sheet) }

    private fun hideSheet() = _ui.update { it.copy(sheet = null) }

    private fun send(effect: WalletEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun WalletState.toChip(): WalletChipState = when (this) {
        WalletState.Disconnected -> WalletChipState.Disconnected
        is WalletState.Connected ->
            if (isSepolia) WalletChipState.Connected(address) else WalletChipState.WrongNetwork(address)
    }
}

/** Custo típico de um registro, para avisar cedo do saldo baixo (FR-003) na folha e na tela Carteira. */
internal object RegistrationCost {
    /** `registerCapture` gasta até ~300 mil de gás (forge --gas-report), mais 20% de margem. */
    private val TYPICAL_GAS: BigInteger = BigInteger.valueOf(360_000)

    fun isLow(balanceWei: BigInteger, gasPriceWei: BigInteger): Boolean = balanceWei < gasPriceWei * TYPICAL_GAS
}
