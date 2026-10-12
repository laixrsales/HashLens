package br.ufv.hashlens.ui.genealogy

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Rota "Histórico da imagem" (ux.md §4.9) do registro do argumento de navegação.
 *
 * @param onClose volta à tela anterior.
 * @param onOpenRecord abre o Histórico de outra versão da árvore.
 */
@Composable
fun GenealogyRoute(
    onClose: () -> Unit,
    onOpenRecord: (recordId: Long) -> Unit,
    viewModel: GenealogyViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                GenealogyEffect.Close -> onClose()
                is GenealogyEffect.OpenRecord -> onOpenRecord(effect.recordId)
                is GenealogyEffect.OpenUrl -> uriHandler.openUri(effect.url)
            }
        }
    }
    BackHandler { onClose() }

    GenealogyScreen(state, onAction = viewModel::onAction)
}
