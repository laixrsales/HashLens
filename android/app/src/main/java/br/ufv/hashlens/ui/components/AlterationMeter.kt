package br.ufv.hashlens.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.PercentFormat
import br.ufv.hashlens.ui.common.motionDuration
import br.ufv.hashlens.ui.preview.ComponentPreview
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme
import br.ufv.hashlens.ui.theme.Motion

private const val MAX_PERCENT = 100f
private const val TICK_STEP = 10
private const val LABEL_STEP = 20

/**
 * Régua de alteração (direção visual §5): régua de evidência de 0 a 100% com o cursor no
 * percentual e o texto de [PercentFormat] abaixo. Mede sem julgar: não há rótulos como "pouco"
 * ou "muito", nem preenchimento de barra de progresso.
 *
 * @param isEditedVersion repassado a [PercentFormat.alteration] (aviso em versões com 0%).
 * @param color cor do status ao qual a medida pertence (padrão: versão alterada).
 */
@Composable
fun AlterationMeter(
    percent: Double,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.alteration_from_original),
    isEditedVersion: Boolean = true,
    color: Color = HashLensTheme.statusColors.edited.content
) {
    val spacing = HashLensTheme.spacing
    val text = PercentFormat.alteration(LocalResources.current, percent, isEditedVersion)
    val target = percent.toFloat().coerceIn(0f, MAX_PERCENT)
    val inspection = LocalInspectionMode.current
    val position = remember { Animatable(if (inspection) target else 0f) }
    val duration = motionDuration(Motion.METER_MS)
    LaunchedEffect(target) { position.animateTo(target, tween(duration)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label: $text" },
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Column {
            Ruler(position.value, color)
            ScaleLabels()
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Ruler(percent: Float, cursorColor: Color) {
    val sizes = HashLensTheme.sizes
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(sizes.icon)
    ) {
        val stroke = sizes.divider.toPx()
        val inset = sizes.cornerMarkLength.toPx() / 4
        val baseline = size.height / 2
        drawLine(lineColor, Offset(inset, baseline), Offset(size.width - inset, baseline), stroke)
        for (tick in 0..MAX_PERCENT.toInt() step TICK_STEP) {
            val x = xOf(tick.toFloat(), inset)
            val major = tick % LABEL_STEP == 0
            val top = if (major) 0f else baseline / 2
            drawLine(lineColor, Offset(x, top), Offset(x, baseline), stroke)
        }
        drawCursor(xOf(percent, inset), baseline, sizes.cornerMarkLength, sizes.cornerMarkStroke, cursorColor)
    }
}

/** Posição de [percent] na régua; [inset] (meio cursor) mantém o cursor inteiro nas pontas. */
private fun DrawScope.xOf(percent: Float, inset: Float): Float =
    inset + (size.width - 2 * inset) * percent / MAX_PERCENT

/** Cursor: traço vertical sobre a régua e triângulo apontando para ela por baixo. */
private fun DrawScope.drawCursor(x: Float, baseline: Float, length: Dp, stroke: Dp, color: Color) {
    val half = length.toPx() / 2
    drawLine(color, Offset(x, 0f), Offset(x, baseline), stroke.toPx())
    val triangle = Path().apply {
        moveTo(x, baseline)
        lineTo(x - half / 2, size.height)
        lineTo(x + half / 2, size.height)
        close()
    }
    drawPath(triangle, color)
}

/** Números 0, 20 … 100 centrados nas marcas maiores; os das pontas encostam nas bordas. */
@Composable
private fun ScaleLabels() {
    val labels = (0..MAX_PERCENT.toInt() step LABEL_STEP).toList()
    val inset = HashLensTheme.sizes.cornerMarkLength / 4
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            labels.forEach {
                Text(
                    it.toString(),
                    style = HashLensTheme.typography.scale,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val insetPx = inset.roundToPx()
        layout(width, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { i, placeable ->
                val center = insetPx + (width - 2 * insetPx) * labels[i] / MAX_PERCENT.toInt()
                val x = (center - placeable.width / 2).coerceIn(0, width - placeable.width)
                placeable.placeRelative(x, 0)
            }
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun AlterationMeterPreview() = ComponentPreview {
    AlterationMeterSamples.All()
}

/** Todos os estados da régua, compartilhados por previews e screenshot tests. */
object AlterationMeterSamples {
    @Composable
    fun All() = SampleColumn {
        AlterationMeter(SampleData.Percent.EDITED)
        AlterationMeter(SampleData.Percent.ZERO)
        AlterationMeter(SampleData.Percent.HEAVY)
        AlterationMeter(SampleData.Percent.MAX)
        AlterationMeter(
            SampleData.Percent.FROM_PARENT,
            label = stringResource(R.string.alteration_from_parent)
        )
        AlterationMeter(
            SampleData.Percent.VISUAL_MATCH,
            isEditedVersion = false,
            color = HashLensTheme.statusColors.visualMatch.content
        )
    }
}
