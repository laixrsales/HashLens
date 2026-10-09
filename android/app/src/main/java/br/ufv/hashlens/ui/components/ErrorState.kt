package br.ufv.hashlens.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.preview.ComponentPreview
import br.ufv.hashlens.ui.theme.HashLensTheme

/**
 * Erro de tela inteira: o que aconteceu (título) e o que fazer (frase e ação), sem pedir
 * desculpas e sem ser vago (hashlens-ui). Ícone em Vermelho óxido.
 *
 * @param actionLabel `null` quando não há o que tentar de novo.
 */
@Composable
fun ErrorState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    MessageState(
        icon = icon,
        iconTint = HashLensTheme.statusColors.failed.content,
        title = title,
        message = message,
        modifier = modifier,
        action = actionLabel?.let { { SecondaryButton(it, onAction) } }
    )
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ErrorStateNetworkPreview() = ComponentPreview {
    ErrorStateSamples.Network()
}

@Preview
@Composable
private fun ErrorStateUnsupportedPreview() = ComponentPreview {
    ErrorStateSamples.UnsupportedFile()
}

@Preview
@Composable
private fun ErrorStateCameraPreview() = ComponentPreview {
    ErrorStateSamples.Camera()
}

/** Erros de tela inteira previstos em ux.md, compartilhados por previews e screenshot tests. */
object ErrorStateSamples {
    @Composable
    fun Network() = ErrorState(
        icon = Icons.Outlined.CloudOff,
        title = stringResource(R.string.verification_network_title),
        message = stringResource(R.string.verification_network_message),
        actionLabel = stringResource(R.string.action_try_again)
    )

    @Composable
    fun UnsupportedFile() = ErrorState(
        icon = Icons.Outlined.BrokenImage,
        title = stringResource(R.string.verification_unsupported_title),
        message = stringResource(R.string.verification_unsupported_message),
        actionLabel = stringResource(R.string.action_choose_another_image)
    )

    @Composable
    fun Camera() = ErrorState(
        icon = Icons.Outlined.NoPhotography,
        title = stringResource(R.string.camera_error_title),
        message = stringResource(R.string.camera_error_message),
        actionLabel = stringResource(R.string.action_try_again)
    )
}
