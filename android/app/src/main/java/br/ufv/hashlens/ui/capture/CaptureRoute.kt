package br.ufv.hashlens.ui.capture

import androidx.activity.compose.BackHandler
import androidx.camera.compose.CameraXViewfinder
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.ufv.hashlens.ui.common.PermissionStatus
import br.ufv.hashlens.ui.common.rememberCameraPermissionState

/**
 * Rota "Registrar foto": câmera e, depois da foto, o Acompanhamento do registro, na mesma tela
 * (o mesmo [CaptureViewModel] acompanha os dois).
 *
 * @param onClose volta ao Início (fechar, "Concluir", "Voltar ao início").
 * @param onViewRecord abre o Resultado da verificação do registro ("Ver registro").
 */
@Composable
fun CaptureRoute(
    onClose: () -> Unit,
    onViewRecord: (recordId: Long) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val permission = rememberCameraPermissionState()
    val camera = rememberCameraController()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CaptureEffect.Close -> onClose()
                is CaptureEffect.OpenUrl -> uriHandler.openUri(effect.url)
                is CaptureEffect.ViewRecord -> onViewRecord(effect.recordId)
                is CaptureEffect.Notice -> snackbar.showSnackbar(
                    effect.arg?.let { resources.getString(effect.text, it) } ?: resources.getString(effect.text)
                )
            }
        }
    }
    // Primeira vez: pede direto, sem explicação (ux.md §4.4)
    LaunchedEffect(permission.status) {
        if (permission.status == PermissionStatus.NotRequested) permission.request()
    }
    BackHandler { onClose() }

    Box(Modifier.fillMaxSize()) {
        val progress = state.progress
        if (progress != null) {
            RegistrationProgressScreen(progress, onAction = viewModel::onProgressAction)
        } else {
            val granted = permission.status == PermissionStatus.Granted
            BindCamera(camera, state.lens, state.camera.flash, enabled = granted, viewModel = viewModel)
            CaptureScreen(
                state = state.camera.withPermission(permission.status),
                onAction = { action ->
                    when (action) {
                        CaptureAction.TakePhoto -> if (viewModel.canTakePhoto()) {
                            camera.takePicture(
                                onPhoto = { photo, rotation ->
                                    viewModel.onPhotoTaken(photo, rotation, photo.toThumbnail(rotation))
                                },
                                onError = { viewModel.onCameraError() }
                            )
                        }
                        CaptureAction.PermissionAction -> permission.onExplanationAction()
                        else -> viewModel.onCameraAction(action)
                    }
                },
                viewfinder = { modifier ->
                    val request by camera.surfaceRequest.collectAsStateWithLifecycle()
                    request?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
                }
            )
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}

@Composable
private fun rememberCameraController(): CameraController {
    val context = LocalContext.current
    return remember(context) { CameraController(context) }
}

/** Liga a câmera da lente atual quando há permissão, e aplica o flash escolhido. */
@Composable
private fun BindCamera(
    camera: CameraController,
    lens: CameraLens,
    flash: FlashMode,
    enabled: Boolean,
    viewModel: CaptureViewModel
) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(enabled, lens, owner) {
        if (!enabled) return@LaunchedEffect
        try {
            viewModel.onCameraBound(camera.bind(owner, lens))
        } catch (_: IllegalArgumentException) {
            // Sem câmera para a lente pedida
            viewModel.onCameraError()
        } catch (_: IllegalStateException) {
            // Câmera em uso por outro app ou fechada pelo sistema
            viewModel.onCameraError()
        }
    }
    LaunchedEffect(flash) { camera.setFlash(flash) }
}

/** Sem permissão, a explicação toma o lugar do visor (ux.md §4.4). */
private fun CaptureUiState.withPermission(status: PermissionStatus): CaptureUiState = when (status) {
    PermissionStatus.Denied, PermissionStatus.PermanentlyDenied -> copy(camera = CameraState.PermissionNeeded(status))
    PermissionStatus.Granted, PermissionStatus.NotRequested -> this
}
