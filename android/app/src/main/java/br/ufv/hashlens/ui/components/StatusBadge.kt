package br.ufv.hashlens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.preview.ComponentPreview
import br.ufv.hashlens.ui.theme.HashLensTheme

/**
 * Selo de status (acervo, indicador do Início): ícone e texto na cor do status sobre o fundo dele.
 *
 * @param text padrão: a palavra do status; o Início usa uma contagem ("2 registrando").
 */
@Composable
fun StatusBadge(status: ImageStatus, modifier: Modifier = Modifier, text: String = stringResource(status.title)) {
    val color = status.color
    val spacing = HashLensTheme.spacing
    Row(
        modifier = modifier
            .background(color.container, MaterialTheme.shapes.small)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Icon(
            status.icon,
            contentDescription = null,
            tint = color.content,
            modifier = Modifier.size(HashLensTheme.sizes.smallIcon)
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = color.content)
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun StatusBadgePreview() = ComponentPreview {
    StatusBadgeSamples.All()
}

/** Um selo por status e a contagem de pendentes do Início. */
object StatusBadgeSamples {
    @Composable
    fun All() = SampleColumn {
        ImageStatus.entries.forEach { StatusBadge(it) }
        StatusBadge(ImageStatus.Pending, text = pluralStringResource(R.plurals.home_library_pending, 2, 2))
    }
}
