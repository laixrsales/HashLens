package br.ufv.hashlens.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.ufv.hashlens.ui.wallet.WalletEffect
import br.ufv.hashlens.ui.wallet.WalletSheet
import br.ufv.hashlens.ui.wallet.WalletViewModel

/**
 * Rota do Início: o hub (ux.md §4.2) e a folha "Conectar carteira" (T056).
 *
 * @param walletListOpen a lista de carteiras do AppKit está por cima; a folha se esconde para não
 *   cobri-la e volta quando ela fecha.
 * @param onOpenWalletList abre a lista de carteiras do AppKit.
 */
@Composable
fun HomeRoute(
    walletListOpen: Boolean,
    onOpenWalletList: () -> Unit,
    onRegisterPhoto: () -> Unit,
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
                WalletEffect.ProceedToCapture -> onRegisterPhoto()
            }
        }
    }

    HomeScreen(
        state = HomeUiState(wallet = wallet.chip, pendingCount = pendingCount),
        onAction = { action ->
            when (action) {
                HomeAction.RegisterPhoto -> walletViewModel.onRegisterPhoto()
                HomeAction.OpenWallet -> walletViewModel.onOpenWallet()
                else -> onNavigate(action)
            }
        }
    )
    val sheet = wallet.sheet
    if (sheet != null && !walletListOpen) WalletSheet(sheet, onAction = walletViewModel::onAction)
}
