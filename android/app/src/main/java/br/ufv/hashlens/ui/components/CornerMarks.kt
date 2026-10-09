package br.ufv.hashlens.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp

/**
 * Cantoneiras (direção visual §5): quatro marcas em "L" nos cantos, que dizem "esta é a imagem
 * sob exame" sem moldura nem sombra. Desenhadas por cima do conteúdo, dentro dos limites dele.
 */
fun Modifier.cornerMarks(color: Color, stroke: Dp, length: Dp): Modifier = drawWithContent {
    drawContent()
    val s = stroke.toPx()
    val l = length.toPx()
    val half = s / 2
    val (w, h) = size.width to size.height
    // Cada canto: ponto do vértice e direções dos dois braços
    listOf(
        Offset(half, half) to (1f to 1f),
        Offset(w - half, half) to (-1f to 1f),
        Offset(half, h - half) to (1f to -1f),
        Offset(w - half, h - half) to (-1f to -1f)
    ).forEach { (corner, direction) ->
        val (dx, dy) = direction
        drawLine(color, corner, corner.copy(x = corner.x + dx * l), s, StrokeCap.Square)
        drawLine(color, corner, corner.copy(y = corner.y + dy * l), s, StrokeCap.Square)
    }
}
