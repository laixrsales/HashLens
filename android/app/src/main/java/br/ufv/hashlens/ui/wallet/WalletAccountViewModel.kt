package br.ufv.hashlens.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.ChainRpc
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.di.IoDispatcher
import br.ufv.hashlens.ui.common.SepoliaEthFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WalletAccountEffect {
    data object Close : WalletAccountEffect

    /** Endereço completo para a área de transferência. */
    data class CopyAddress(val address: String) : WalletAccountEffect

    data class OpenUrl(val url: String) : WalletAccountEffect
}

/**
 * Tela Carteira (T091, ux.md §4.11): acompanha a sessão e consulta o saldo de teste da conta. Ao
 * desconectar, por aqui ou pelo app da carteira, a tela fecha; câmera e edição voltam a pedir a
 * carteira ([WalletGate]).
 *
 * @property ui `null` sem carteira conectada (a tela está fechando).
 */
@HiltViewModel
class WalletAccountViewModel @Inject constructor(
    private val session: WalletSession,
    private val rpc: ChainRpc,
    private val config: ChainConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val _ui = MutableStateFlow<WalletAccountUiState?>(null)
    val ui: StateFlow<WalletAccountUiState?> = _ui.asStateFlow()

    private val _effects = Channel<WalletAccountEffect>(Channel.BUFFERED)
    val effects: Flow<WalletAccountEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            session.state.collect { state ->
                when (state) {
                    WalletState.Disconnected -> {
                        _ui.value = null
                        send(WalletAccountEffect.Close)
                    }
                    is WalletState.Connected -> show(state)
                }
            }
        }
    }

    fun onAction(action: WalletAccountAction) {
        when (action) {
            WalletAccountAction.Back -> send(WalletAccountEffect.Close)
            WalletAccountAction.CopyAddress -> _ui.value?.let { send(WalletAccountEffect.CopyAddress(it.address)) }
            WalletAccountAction.OpenFaucet -> send(WalletAccountEffect.OpenUrl(config.faucetUrl))
            WalletAccountAction.SwitchToSepolia -> viewModelScope.launch {
                // Recusa ou rede desconhecida na carteira: a tela segue pedindo a troca
                try {
                    session.switchToSepolia()
                } catch (_: WalletException) {
                }
            }
            // A sessão vira Disconnected e a tela fecha pelo coletor acima
            WalletAccountAction.Disconnect -> viewModelScope.launch { session.disconnect() }
        }
    }

    private suspend fun show(state: WalletState.Connected) {
        val current = _ui.value?.takeIf { it.address.equals(state.address, ignoreCase = true) }
        _ui.value = (current ?: WalletAccountUiState(state.address)).copy(onSepolia = state.isSepolia)
        if (current?.balance != null) return
        val reading = withContext(ioDispatcher) {
            try {
                rpc.getBalance(state.address) to rpc.gasPrice()
            } catch (_: RegistryException) {
                null
            }
        }
        _ui.value = _ui.value?.let { ui ->
            if (reading == null) {
                ui.copy(balance = null, balanceUnavailable = true, lowBalance = false)
            } else {
                val (balance, gasPrice) = reading
                ui.copy(
                    balance = SepoliaEthFormat.fromWei(balance).stripTrailingZeros(),
                    balanceUnavailable = false,
                    lowBalance = RegistrationCost.isLow(balance, gasPrice)
                )
            }
        }
    }

    private fun send(effect: WalletAccountEffect) {
        _effects.trySend(effect)
    }
}
