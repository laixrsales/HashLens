package br.ufv.hashlens.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.ufv.hashlens.R

/**
 * Rota "Carteira" (ux.md §4.11), aberta pelo chip do Início com uma carteira conectada.
 *
 * @param onClose volta à tela anterior; também ao desconectar.
 */
@Composable
fun WalletAccountRoute(onClose: () -> Unit, viewModel: WalletAccountViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WalletAccountEffect.Close -> onClose()
                is WalletAccountEffect.OpenUrl -> uriHandler.openUri(effect.url)
                is WalletAccountEffect.CopyAddress -> {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(resources.getString(R.string.wallet_account_address), effect.address)
                    )
                    // A partir do Android 13 o próprio sistema confirma a cópia
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        snackbar.showSnackbar(resources.getString(R.string.wallet_account_copied))
                    }
                }
            }
        }
    }
    BackHandler { onClose() }

    Box(Modifier.fillMaxSize()) {
        state?.let { WalletAccountScreen(it, onAction = viewModel::onAction) }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}
