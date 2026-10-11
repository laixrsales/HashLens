package br.ufv.hashlens.ui.verify

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Rota "Verificar imagem": Verificar imagem e, com a resposta, o Resultado, na mesma tela (o mesmo
 * [VerifyViewModel] acompanha os dois). A imagem vem só do Photo Picker do sistema (FR-019), que
 * não pede permissão de acesso às fotos.
 *
 * @param onClose volta à tela anterior.
 * @param onOpenHistory abre o Histórico da imagem do registro.
 */
@Composable
fun VerifyRoute(
    onClose: () -> Unit,
    onOpenHistory: (recordId: Long) -> Unit,
    viewModel: VerifyViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), viewModel::onImagePicked)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                VerifyEffect.Close -> onClose()
                VerifyEffect.OpenPicker ->
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                is VerifyEffect.OpenHistory -> onOpenHistory(effect.recordId)
                is VerifyEffect.OpenUrl -> uriHandler.openUri(effect.url)
            }
        }
    }
    BackHandler { onClose() }

    val result = state.result
    if (result != null) {
        VerificationResultScreen(result, onAction = viewModel::onResultAction)
    } else {
        VerifyScreen(state.verify, onAction = viewModel::onVerifyAction)
    }
}
