package br.ufv.hashlens.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.motionDuration
import br.ufv.hashlens.ui.preview.ComponentPreview
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme
import br.ufv.hashlens.ui.theme.Motion

private const val EXPANDED_ROTATION = 180f

/** Um dado técnico: rótulo do glossário (ux.md §6) e o valor completo, selecionável para copiar. */
data class TechnicalItem(@StringRes val label: Int, val value: String, val url: String? = null)

/**
 * "Detalhes técnicos" (FR-033): hashes, endereço completo, ids e transação, fora da camada
 * principal. Recolhido por padrão; o estado fica com quem chama.
 *
 * @param onOpenUrl abre o link de um item (explorador da rede).
 */
@Composable
fun TechnicalDetails(
    items: List<TechnicalItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = HashLensTheme.spacing
    val duration = motionDuration(Motion.EXPAND_MS)
    val rotation by animateFloatAsState(if (expanded) EXPANDED_ROTATION else 0f, tween(duration), label = "seta")
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)

    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = HashLensTheme.sizes.divider, color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HashLensTheme.sizes.minTouchTarget)
                .toggleable(value = expanded, role = Role.Button, onValueChange = onExpandedChange)
                .semantics { stateDescription = state },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.technical_details),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(duration)),
            exit = shrinkVertically(tween(duration))
        ) {
            Column(
                modifier = Modifier.padding(bottom = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                items.forEach { TechnicalRow(it, onOpenUrl) }
            }
        }
    }
}

@Composable
private fun TechnicalRow(item: TechnicalItem, onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)) {
        Text(
            stringResource(item.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(item.value, style = HashLensTheme.typography.technical, color = MaterialTheme.colorScheme.onSurface)
        }
        item.url?.let { url ->
            TextButton(
                onClick = { onOpenUrl(url) },
                shape = MaterialTheme.shapes.medium,
                // Sem recuo à esquerda: o link alinha com o rótulo e o valor acima
                contentPadding = PaddingValues(end = HashLensTheme.spacing.sm)
            ) {
                Text(stringResource(R.string.technical_open_explorer), style = MaterialTheme.typography.labelLarge)
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = HashLensTheme.spacing.xs)
                        .size(HashLensTheme.sizes.smallIcon)
                )
            }
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun TechnicalDetailsExpandedPreview() = ComponentPreview {
    TechnicalDetailsSamples.Expanded()
}

@Preview
@Composable
private fun TechnicalDetailsCollapsedPreview() = ComponentPreview {
    TechnicalDetailsSamples.Collapsed()
}

/** Estados recolhido e expandido, compartilhados por previews e screenshot tests. */
object TechnicalDetailsSamples {
    @Composable
    fun Collapsed() = Interactive(initiallyExpanded = false)

    @Composable
    fun Expanded() = Interactive(initiallyExpanded = true)

    @Composable
    private fun Interactive(initiallyExpanded: Boolean) {
        var expanded by remember { mutableStateOf(initiallyExpanded) }
        TechnicalDetails(
            items = SampleData.Technical.edited,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onOpenUrl = {}
        )
    }
}
