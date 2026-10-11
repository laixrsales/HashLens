package br.ufv.hashlens.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.ui.capture.CameraIconButton
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.ui.common.EditOperationFormat
import br.ufv.hashlens.ui.theme.HashLensTheme
import kotlin.math.roundToInt

// Controles do Editor (EditorScreen): sobre o fundo escuro da foto, nas cores da câmera

private const val SELECTED_ALPHA = 0.16f
private const val DISABLED_ALPHA = 0.38f

/** Fechar à esquerda; desfazer e refazer à direita. */
@Composable
internal fun EditorTopBar(state: EditorUiState.Editing, onAction: (EditorAction) -> Unit) {
    val enabled = !state.processing
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CameraIconButton(Icons.Outlined.Close, stringResource(R.string.action_close), enabled = enabled) {
            onAction(EditorAction.Close)
        }
        Spacer(Modifier.weight(1f))
        CameraIconButton(
            Icons.AutoMirrored.Outlined.Undo,
            stringResource(R.string.action_undo),
            enabled = enabled && state.canUndo
        ) { onAction(EditorAction.Undo) }
        Spacer(Modifier.size(HashLensTheme.spacing.sm))
        CameraIconButton(
            Icons.AutoMirrored.Outlined.Redo,
            stringResource(R.string.action_redo),
            enabled = enabled && state.canRedo
        ) { onAction(EditorAction.Redo) }
    }
}

/** Faixa "Registrada originalmente por 0x12ab…9f3c" em imagem de outra pessoa (ux.md §4.6). */
@Composable
internal fun ThirdPartyBanner(author: String, modifier: Modifier = Modifier) {
    val camera = HashLensTheme.cameraColors
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            // Superfície do tema escuro: o véu da câmera some sobre o fundo preto do Editor
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = HashLensTheme.spacing.md, vertical = HashLensTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)
    ) {
        // Decorativo: o texto diz de quem é a original
        Icon(
            Icons.Outlined.Verified,
            contentDescription = null,
            tint = camera.content,
            modifier = Modifier.size(HashLensTheme.sizes.smallIcon)
        )
        Text(
            stringResource(R.string.editor_third_party, AddressFormat.short(author)),
            style = MaterialTheme.typography.labelMedium,
            color = camera.content,
            textAlign = TextAlign.Center
        )
    }
}

/** Faixa de filtros na zona do polegar; rola na horizontal. */
@Composable
internal fun ToolStrip(activeTool: EditorTool?, enabled: Boolean, onAction: (EditorAction) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(EditorTool.entries) { tool ->
            ToolButton(tool, selected = tool == activeTool, enabled = enabled) {
                onAction(EditorAction.SelectTool(tool))
            }
        }
    }
}

@Composable
private fun ToolButton(tool: EditorTool, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val camera = HashLensTheme.cameraColors
    val spacing = HashLensTheme.spacing
    val content = if (enabled) camera.content else camera.content.copy(alpha = DISABLED_ALPHA)
    Column(
        Modifier
            .widthIn(min = HashLensTheme.sizes.toolButtonWidth)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) camera.content.copy(alpha = SELECTED_ALPHA) else camera.background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        // Decorativo: o rótulo diz o filtro
        Icon(
            tool.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(HashLensTheme.sizes.icon)
        )
        Text(stringResource(tool.label), style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/**
 * Ajuste da ferramenta ativa: nome e valor, o controle deslizante (ou a dica do recorte) e as
 * ações de cancelar e aplicar. O ajuste já aparece na prévia; só entra na lista ao aplicar.
 */
@Composable
internal fun AdjustmentPanel(draft: EditOperation, enabled: Boolean, onAction: (EditorAction) -> Unit) {
    val camera = HashLensTheme.cameraColors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CameraIconButton(Icons.Outlined.Close, stringResource(R.string.action_cancel), enabled = enabled) {
                onAction(EditorAction.CancelDraft)
            }
            Text(
                draft.label(),
                style = MaterialTheme.typography.titleMedium,
                color = camera.content,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = HashLensTheme.spacing.sm)
            )
            CameraIconButton(Icons.Outlined.Check, stringResource(R.string.action_apply), enabled = enabled) {
                onAction(EditorAction.ApplyDraft)
            }
        }
        when (draft) {
            is EditOperation.Brightness -> ParameterSlider(
                value = draft.delta.toFloat(),
                range = BRIGHTNESS_RANGE,
                steps = 0,
                enabled = enabled
            ) { onAction(EditorAction.UpdateDraft(EditOperation.Brightness(it.roundToInt()))) }
            is EditOperation.Contrast -> ParameterSlider(
                value = draft.factor,
                range = CONTRAST_RANGE,
                steps = 0,
                enabled = enabled
            ) {
                onAction(EditorAction.UpdateDraft(EditOperation.Contrast((it * HUNDREDTHS).roundToInt() / HUNDREDTHS)))
            }
            is EditOperation.Blur -> ParameterSlider(
                // Só núcleos ímpares: a posição n vale 2n + 1 (3, 5, …, 25)
                value = ((draft.kernel - 1) / 2).toFloat(),
                range = BLUR_POSITIONS,
                steps = BLUR_STEPS,
                enabled = enabled
            ) { onAction(EditorAction.UpdateDraft(EditOperation.Blur(it.roundToInt() * 2 + 1))) }
            is EditOperation.Edges -> ParameterSlider(
                value = draft.low.toFloat(),
                range = EDGES_RANGE,
                steps = 0,
                enabled = enabled
            ) { onAction(EditorAction.UpdateDraft(edgesOf(it.roundToInt()))) }
            is EditOperation.Crop -> Text(
                stringResource(R.string.editor_crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = camera.content,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            else -> Unit
        }
    }
}

/** Nome do ajuste e o valor atual, como na lista de operações ("Brilho +30", "Bordas"). */
@Composable
private fun EditOperation.label(): String = when (this) {
    is EditOperation.Edges -> stringResource(R.string.tool_edges_sensitivity, low)
    else -> EditOperationFormat.one(LocalResources.current, this)
}

@Composable
private fun ParameterSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    val camera = HashLensTheme.cameraColors
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = camera.content,
            activeTrackColor = camera.content,
            inactiveTrackColor = camera.content.copy(alpha = SELECTED_ALPHA),
            // Sem marcas de passo: o valor aparece no título do ajuste
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
    )
}

// Faixas de hashing-spec §4
private val BRIGHTNESS_RANGE = -100f..100f
private val CONTRAST_RANGE = 0.5f..2f
private const val HUNDREDTHS = 100f
private val BLUR_POSITIONS = 1f..12f
private const val BLUR_STEPS = 10
private val EDGES_RANGE = 0f..255f
