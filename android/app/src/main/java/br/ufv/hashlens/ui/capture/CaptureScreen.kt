package br.ufv.hashlens.ui.capture

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.PermissionStatus
import br.ufv.hashlens.ui.components.ErrorState
import br.ufv.hashlens.ui.components.MessageState
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.cornerMarks
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme

/** Estado da Câmera (ux.md §4.4). */
data class CaptureUiState(
    val camera: CameraState = CameraState.Ready,
    val flash: FlashMode = FlashMode.Off,
    /** Falso em aparelhos com uma câmera só. */
    val canSwitchCamera: Boolean = true
)

sealed interface CameraState {
    data object Ready : CameraState

    /** Foto tirada: codificando e calculando os identificadores antes do registro. */
    data object Processing : CameraState

    /** Sem permissão; [status] decide entre pedir de novo e abrir as configurações. */
    data class PermissionNeeded(val status: PermissionStatus) : CameraState

    data object Error : CameraState
}

/** Ordem de [CaptureAction.ToggleFlash]: desligado → ligado → automático. */
enum class FlashMode(val icon: ImageVector, @StringRes val description: Int) {
    Off(Icons.Outlined.FlashOff, R.string.flash_off),
    On(Icons.Outlined.FlashOn, R.string.flash_on),
    Auto(Icons.Outlined.FlashAuto, R.string.flash_auto)
}

sealed interface CaptureAction {
    data object TakePhoto : CaptureAction

    data object SwitchCamera : CaptureAction

    data object ToggleFlash : CaptureAction

    data object Close : CaptureAction

    /** Ação da explicação de permissão: pedir de novo ou abrir as configurações. */
    data object PermissionAction : CaptureAction

    data object Retry : CaptureAction
}

/**
 * Câmera (ux.md §4.4): visor em tela cheia, sempre escuro, com os controles na zona do polegar.
 * Sem permissão ou com erro, a tela volta ao tema do app, porque não há visor.
 *
 * @param viewfinder visor da câmera (CameraX, ligado em T057), desenhado atrás dos controles.
 */
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onAction: (CaptureAction) -> Unit,
    modifier: Modifier = Modifier,
    viewfinder: @Composable (Modifier) -> Unit = {}
) {
    when (val camera = state.camera) {
        CameraState.Ready, CameraState.Processing -> Viewfinder(state, onAction, modifier, viewfinder)
        is CameraState.PermissionNeeded -> ThemedMessage(onAction, modifier) {
            // Não é erro: a pessoa ainda pode permitir. Ícone neutro e ação primária
            val explanation = camera.status.cameraExplanation
            MessageState(
                icon = Icons.Outlined.PhotoCamera,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = stringResource(explanation?.title ?: R.string.camera_permission_title),
                message = stringResource(explanation?.message ?: R.string.camera_permission_message)
            ) {
                PrimaryButton(
                    stringResource(explanation?.action ?: R.string.camera_permission_allow),
                    onClick = { onAction(CaptureAction.PermissionAction) }
                )
            }
        }
        CameraState.Error -> ThemedMessage(onAction, modifier) {
            ErrorState(
                icon = Icons.Outlined.NoPhotography,
                title = stringResource(R.string.camera_error_title),
                message = stringResource(R.string.camera_error_message),
                actionLabel = stringResource(R.string.action_try_again),
                onAction = { onAction(CaptureAction.Retry) }
            )
        }
    }
}

@Composable
private fun Viewfinder(
    state: CaptureUiState,
    onAction: (CaptureAction) -> Unit,
    modifier: Modifier,
    viewfinder: @Composable (Modifier) -> Unit
) {
    val camera = HashLensTheme.cameraColors
    val spacing = HashLensTheme.spacing
    val processing = state.camera == CameraState.Processing
    Box(
        modifier
            .fillMaxSize()
            .background(camera.background)
    ) {
        viewfinder(Modifier.fillMaxSize())
        // Processando: o visor escurece para deixar claro que a câmera está ocupada
        if (processing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(camera.scrim)
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopControls(state.flash, processing, onAction)
            // Cantoneiras brancas: "esta é a imagem que será registrada" (direção visual §5)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = spacing.xl)
                    .cornerMarks(
                        camera.content,
                        HashLensTheme.sizes.cornerMarkStroke,
                        HashLensTheme.sizes.cornerMarkLength
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (processing) ProcessingLabel()
            }
            BottomControls(state.canSwitchCamera, processing, onAction)
        }
    }
}

/** Fechar, a indicação de registro e o flash, no topo. */
@Composable
private fun TopControls(flash: FlashMode, processing: Boolean, onAction: (CaptureAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CameraIconButton(Icons.Outlined.Close, stringResource(R.string.action_close)) {
            onAction(CaptureAction.Close)
        }
        // Só o espaço entre os botões: com fonte grande, a etiqueta quebra a linha
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = HashLensTheme.spacing.sm),
            contentAlignment = Alignment.Center
        ) { RegisterHint() }
        CameraIconButton(flash.icon, stringResource(flash.description), enabled = !processing) {
            onAction(CaptureAction.ToggleFlash)
        }
    }
}

/** Disparador no centro e trocar câmera à direita, na zona do polegar. */
@Composable
private fun BottomControls(canSwitchCamera: Boolean, processing: Boolean, onAction: (CaptureAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Equilibra o botão de trocar câmera, para o disparador ficar no centro
        Spacer(Modifier.size(HashLensTheme.sizes.minTouchTarget))
        Shutter(enabled = !processing, onClick = { onAction(CaptureAction.TakePhoto) })
        if (canSwitchCamera) {
            CameraIconButton(
                Icons.Outlined.Cameraswitch,
                stringResource(R.string.action_switch_camera),
                enabled = !processing
            ) { onAction(CaptureAction.SwitchCamera) }
        } else {
            Spacer(Modifier.size(HashLensTheme.sizes.minTouchTarget))
        }
    }
}

/** Sem visor: tema do app, com o botão de fechar e a mensagem no centro. */
@Composable
private fun ThemedMessage(onAction: (CaptureAction) -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(HashLensTheme.spacing.lg)
        ) {
            IconButton(onClick = { onAction(CaptureAction.Close) }) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
        }
    }
}

/** Visor fictício dos previews: a foto de exemplo no lugar do CameraX. */
@Composable
private fun SampleViewfinder(modifier: Modifier) {
    Image(
        SampleData.Photo.landscape(),
        contentDescription = stringResource(R.string.camera_viewfinder_description),
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun CaptureReadyPreview() = HashLensTheme { CaptureSamples.Ready() }

@Preview
@Composable
private fun CaptureProcessingPreview() = HashLensTheme { CaptureSamples.Processing() }

@PreviewLightDark
@Composable
private fun CapturePermissionPreview() = HashLensTheme { CaptureSamples.PermissionDenied() }

@Preview
@Composable
private fun CapturePermissionSettingsPreview() = HashLensTheme { CaptureSamples.PermissionPermanentlyDenied() }

@Preview
@Composable
private fun CaptureErrorPreview() = HashLensTheme { CaptureSamples.Error() }

/** Estados da Câmera (ux.md §4.4), compartilhados por previews e screenshot tests. */
object CaptureSamples {
    @Composable
    fun Ready() = CaptureScreen(CaptureUiState(), onAction = {}, viewfinder = { SampleViewfinder(it) })

    @Composable
    fun Processing() = CaptureScreen(
        CaptureUiState(camera = CameraState.Processing, flash = FlashMode.Auto),
        onAction = {},
        viewfinder = { SampleViewfinder(it) }
    )

    @Composable
    fun PermissionDenied() =
        CaptureScreen(CaptureUiState(camera = CameraState.PermissionNeeded(PermissionStatus.Denied)), onAction = {})

    @Composable
    fun PermissionPermanentlyDenied() = CaptureScreen(
        CaptureUiState(camera = CameraState.PermissionNeeded(PermissionStatus.PermanentlyDenied)),
        onAction = {}
    )

    @Composable
    fun Error() = CaptureScreen(CaptureUiState(camera = CameraState.Error), onAction = {})
}
