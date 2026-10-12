package br.ufv.hashlens.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.ufv.hashlens.ui.wallet.WalletEffect
import br.ufv.hashlens.ui.wallet.WalletSheet
import br.ufv.hashlens.ui.wallet.WalletViewModel

/**
 * Rota "Meu acervo" (ux.md §4.10). "Registrar foto" do estado vazio passa pela carteira como no
 * Início (folha "Conectar carteira" quando preciso) e só então abre a câmera.
 *
 * @param walletListOpen a lista de carteiras do AppKit está por cima; a folha se esconde para não
 *   cobri-la e volta quando ela fecha.
 * @param onOpenWalletList abre a lista de carteiras do AppKit.
 * @param onOpenHistory abre o Histórico da imagem do registro.
 * @param onEdit abre o Editor com a foto do acervo.
 */
@Suppress("LongParameterList")
@Composable
fun LibraryRoute(
    walletListOpen: Boolean,
    onOpenWalletList: () -> Unit,
    onClose: () -> Unit,
    onRegisterPhoto: () -> Unit,
    onOpenHistory: (recordId: Long) -> Unit,
    onEdit: (localId: Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val wallet by walletViewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LibraryEffect.Close -> onClose()
                LibraryEffect.RegisterPhoto -> walletViewModel.onRegisterPhoto()
                is LibraryEffect.OpenHistory -> onOpenHistory(effect.recordId)
                is LibraryEffect.Edit -> onEdit(effect.localId)
                is LibraryEffect.Notice -> snackbar.showSnackbar(
                    effect.arg?.let { resources.getString(effect.text, it) } ?: resources.getString(effect.text)
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        walletViewModel.effects.collect { effect ->
            when (effect) {
                WalletEffect.OpenWalletList -> onOpenWalletList()
                is WalletEffect.OpenUrl -> uriHandler.openUri(effect.url)
                WalletEffect.ProceedToCapture -> onRegisterPhoto()
            }
        }
    }
    BackHandler { onClose() }

    Box(Modifier.fillMaxSize()) {
        LibraryScreen(state, onAction = viewModel::onAction)
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
    val sheet = wallet.sheet
    if (sheet != null && !walletListOpen) WalletSheet(sheet, onAction = walletViewModel::onAction)
}
