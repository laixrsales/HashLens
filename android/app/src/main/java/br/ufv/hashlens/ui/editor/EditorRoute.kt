package br.ufv.hashlens.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import br.ufv.hashlens.ui.capture.RegistrationProgressScreen

/**
 * Rota "Editar": o Editor e, depois de "Salvar e registrar", o Acompanhamento do registro da
 * versão, na mesma tela (o mesmo [EditorViewModel] acompanha os dois). Em "Importar para editar",
 * começa pelo Photo Picker do sistema, que não pede permissão de acesso às fotos.
 *
 * @param onClose volta à tela anterior.
 * @param onViewRecord abre o Resultado da verificação do registro ("Ver registro").
 */
@Composable
fun EditorRoute(
    onClose: () -> Unit,
    onViewRecord: (recordId: Long) -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), viewModel::onImagePicked)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditorEffect.Close -> onClose()
                is EditorEffect.OpenUrl -> uriHandler.openUri(effect.url)
                is EditorEffect.ViewRecord -> onViewRecord(effect.recordId)
                EditorEffect.OpenPicker ->
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                is EditorEffect.Notice -> snackbar.showSnackbar(
                    effect.arg?.let { resources.getString(effect.text, it) } ?: resources.getString(effect.text)
                )
            }
        }
    }
    BackHandler { if (state.progress != null) onClose() else viewModel.onBack() }

    Box(Modifier.fillMaxSize()) {
        val progress = state.progress
        if (progress != null) {
            RegistrationProgressScreen(progress, onAction = viewModel::onProgressAction)
        } else {
            EditorScreen(state.editor, onAction = viewModel::onAction)
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}
