package br.ufv.hashlens.ui.home

import br.ufv.hashlens.ui.components.WalletChipState

/**
 * Estado do Início (ux.md §4.2): carteira no chip do topo e registros pendentes no acervo.
 *
 * @property pendingCount registros "Registrando…" neste aparelho; 0 esconde o indicador.
 */
data class HomeUiState(val wallet: WalletChipState = WalletChipState.Disconnected, val pendingCount: Int = 0)

/** Ações do Início. A navegação fica com quem liga a tela (T057, T065, …). */
sealed interface HomeAction {
    data object VerifyImage : HomeAction

    data object RegisterPhoto : HomeAction

    data object OpenLibrary : HomeAction

    data object ImportForEdit : HomeAction

    data object OpenWallet : HomeAction

    data object HowItWorks : HomeAction
}
