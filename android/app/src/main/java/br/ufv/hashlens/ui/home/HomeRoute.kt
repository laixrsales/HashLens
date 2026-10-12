package br.ufv.hashlens.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.ufv.hashlens.ui.components.WalletChipState
import br.ufv.hashlens.ui.wallet.WalletEffect
import br.ufv.hashlens.ui.wallet.WalletGate
import br.ufv.hashlens.ui.wallet.WalletSheet
import br.ufv.hashlens.ui.wallet.WalletViewModel

/**
 * Rota do Início: o hub (ux.md §4.2) e a folha "Conectar carteira" (T056). Registrar e importar
 * para editar passam pela carteira (guarda de sessão, T091); com uma conta conectada, o chip abre a
 * tela Carteira.
 *
 * @param walletListOpen a lista de carteiras do AppKit está por cima; a folha se esconde para não
 *   cobri-la e volta quando ela fecha.
 * @param onOpenWalletList abre a lista de carteiras do AppKit.
 * @param onRegisterPhoto abre a câmera, com a carteira já pronta.
 * @param onImportForEdit abre o Editor pela importação, com a carteira já pronta.
 * @param onOpenWalletAccount abre a tela Carteira.
 */
@Composable
fun HomeRoute(
    walletListOpen: Boolean,
    onOpenWalletList: () -> Unit,
    onRegisterPhoto: () -> Unit,
    onImportForEdit: () -> Unit,
    onOpenWalletAccount: () -> Unit,
    onNavigate: (HomeAction) -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val pendingCount by homeViewModel.pendingCount.collectAsStateWithLifecycle()
    val wallet by walletViewModel.ui.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        walletViewModel.effects.collect { effect ->
            when (effect) {
                WalletEffect.OpenWalletList -> onOpenWalletList()
                is WalletEffect.OpenUrl -> uriHandler.openUri(effect.url)
                is WalletEffect.Proceed -> when (effect.gate) {
                    WalletGate.Capture -> onRegisterPhoto()
                    WalletGate.ImportForEdit -> onImportForEdit()
                    // O Início não abre uma foto do acervo
                    is WalletGate.Edit -> Unit
                }
            }
        }
    }

    HomeScreen(
        state = HomeUiState(wallet = wallet.chip, pendingCount = pendingCount),
        onAction = { action ->
            when (action) {
                HomeAction.RegisterPhoto -> walletViewModel.requireWallet(WalletGate.Capture)
                HomeAction.ImportForEdit -> walletViewModel.requireWallet(WalletGate.ImportForEdit)
                // Sem carteira, a folha para conectar; com uma conta (mesmo em outra rede), a tela Carteira
                HomeAction.OpenWallet ->
                    if (wallet.chip ==
                        WalletChipState.Disconnected
                    ) {
                        walletViewModel.onOpenWallet()
                    } else {
                        onOpenWalletAccount()
                    }
                else -> onNavigate(action)
            }
        }
    )
    val sheet = wallet.sheet
    if (sheet != null && !walletListOpen) WalletSheet(sheet, onAction = walletViewModel::onAction)
}
