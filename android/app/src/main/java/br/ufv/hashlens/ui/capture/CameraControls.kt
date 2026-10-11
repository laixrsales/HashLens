package br.ufv.hashlens.ui.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.theme.HashLensTheme

// Controles sobre o visor da Câmera (CaptureScreen): sempre nas cores da câmera, nos dois temas

private const val DISABLED_ALPHA = 0.38f

/** Indicação discreta de que a foto será registrada, sobre um véu para ler em qualquer fundo. */
@Composable
internal fun RegisterHint() {
    val camera = HashLensTheme.cameraColors
    Row(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(camera.scrim)
            .padding(horizontal = HashLensTheme.spacing.md, vertical = HashLensTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)
    ) {
        Icon(
            Icons.Outlined.Verified,
            contentDescription = null,
            tint = camera.content,
            modifier = Modifier.size(HashLensTheme.sizes.smallIcon)
        )
        Text(
            stringResource(R.string.camera_will_register),
            style = MaterialTheme.typography.labelMedium,
            color = camera.content,
            textAlign = TextAlign.Center
        )
    }
}

/** Espera sobre o visor ou a foto, sempre com texto (direção visual §6). */
@Composable
internal fun ProcessingLabel(text: String = stringResource(R.string.camera_processing)) {
    val camera = HashLensTheme.cameraColors
    Row(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(camera.scrim)
            .padding(HashLensTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.md)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(HashLensTheme.sizes.icon),
            color = camera.content,
            strokeWidth = HashLensTheme.sizes.cornerMarkStroke
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = camera.content
        )
    }
}

/** Disparador: anel e disco em Papel, 72 dp, na zona do polegar. */
@Composable
internal fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val camera = HashLensTheme.cameraColors
    val color = if (enabled) camera.content else camera.content.copy(alpha = DISABLED_ALPHA)
    val description = stringResource(R.string.action_take_photo)
    Box(
        Modifier
            .size(HashLensTheme.sizes.shutter)
            .clip(CircleShape)
            .border(BorderStroke(HashLensTheme.sizes.cornerMarkStroke * 2, color), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(HashLensTheme.spacing.sm)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
internal fun CameraIconButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val camera = HashLensTheme.cameraColors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = camera.scrim,
            contentColor = camera.content,
            disabledContainerColor = camera.scrim,
            disabledContentColor = camera.content.copy(alpha = DISABLED_ALPHA)
        )
    ) {
        Icon(icon, contentDescription = description)
    }
}
