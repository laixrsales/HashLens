package br.ufv.hashlens.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.preview.ComponentPreview

/** Vazio é um convite à ação (hashlens-ui): diz o que aparecerá aqui e como começar. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    MessageState(
        icon = icon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = title,
        message = message,
        modifier = modifier,
        action = { PrimaryButton(actionLabel, onAction) }
    )
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun EmptyStatePreview() = ComponentPreview {
    EmptyStateSamples.Library()
}

/** Acervo vazio (ux.md §4.10), compartilhado por previews e screenshot tests. */
object EmptyStateSamples {
    @Composable
    fun Library() = EmptyState(
        icon = Icons.Outlined.PhotoLibrary,
        title = stringResource(R.string.library_empty_title),
        message = stringResource(R.string.library_empty_message),
        actionLabel = stringResource(R.string.action_register_photo),
        onAction = {}
    )
}
