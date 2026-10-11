package br.ufv.hashlens.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.ui.theme.HashLensTheme
import kotlin.math.min

/**
 * Moldura do recorte sobre a foto: escurece o que fica de fora e tem alças em "L" nos cantos, como
 * as cantoneiras (direção visual §5). Arrastar um canto muda o recorte, em frações da imagem.
 *
 * @param imageWidth largura da prévia, para achar onde a foto ficou dentro da área (`ContentScale.Fit`).
 */
@Composable
internal fun CropFrame(
    crop: EditOperation.Crop,
    imageWidth: Int,
    imageHeight: Int,
    onChange: (EditOperation.Crop) -> Unit,
    modifier: Modifier = Modifier
) {
    val camera = HashLensTheme.cameraColors
    val sizes = HashLensTheme.sizes
    val description = stringResource(R.string.editor_crop_frame)
    val current by rememberUpdatedState(crop)
    Canvas(
        modifier
            .pointerInput(imageWidth, imageHeight) {
                var corner: Corner? = null
                detectDragGestures(
                    onDragStart = { start ->
                        val area = fitted(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        corner = Corner.nearest(frameOf(current, area), start)
                    },
                    onDragEnd = { corner = null },
                    onDragCancel = { corner = null },
                    onDrag = { change, drag ->
                        val dragged = corner ?: return@detectDragGestures
                        change.consume()
                        val area = fitted(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        onChange(current.moved(dragged, drag.x / area.width, drag.y / area.height))
                    }
                )
            }
            .semantics { contentDescription = description }
    ) {
        val area = fitted(size.width, size.height, imageWidth, imageHeight)
        val frame = frameOf(crop, area)
        // Fora da moldura, dentro da foto: escurecido
        drawRect(camera.scrim, Offset(area.left, area.top), Size(area.width, frame.top - area.top))
        drawRect(camera.scrim, Offset(area.left, frame.bottom), Size(area.width, area.bottom - frame.bottom))
        drawRect(camera.scrim, Offset(area.left, frame.top), Size(frame.left - area.left, frame.height))
        drawRect(camera.scrim, Offset(frame.right, frame.top), Size(area.right - frame.right, frame.height))
        drawRect(camera.content, frame.topLeft, frame.size, style = Stroke(sizes.divider.toPx()))
        drawHandles(frame, camera.content, sizes.cornerMarkStroke.toPx() * 2, sizes.cornerMarkLength.toPx())
    }
}

/** Alças em "L" nos quatro cantos da moldura. */
private fun DrawScope.drawHandles(frame: Rect, color: Color, stroke: Float, length: Float) {
    Corner.entries.forEach { corner ->
        val point = corner.of(frame)
        val dx = if (corner.left) length else -length
        val dy = if (corner.top) length else -length
        drawLine(color, point, point.copy(x = point.x + dx), stroke)
        drawLine(color, point, point.copy(y = point.y + dy), stroke)
    }
}

/** Onde a foto fica dentro da área com `ContentScale.Fit`: centralizada, sem cortar. */
private fun fitted(width: Float, height: Float, imageWidth: Int, imageHeight: Int): Rect {
    val scale = min(width / imageWidth, height / imageHeight)
    val w = imageWidth * scale
    val h = imageHeight * scale
    return Rect(Offset((width - w) / 2, (height - h) / 2), Size(w, h))
}

private fun frameOf(crop: EditOperation.Crop, area: Rect) = Rect(
    Offset(area.left + crop.x * area.width, area.top + crop.y * area.height),
    Size(crop.w * area.width, crop.h * area.height)
)

private enum class Corner(val left: Boolean, val top: Boolean) {
    TopLeft(left = true, top = true),
    TopRight(left = false, top = true),
    BottomLeft(left = true, top = false),
    BottomRight(left = false, top = false);

    fun of(frame: Rect): Offset = Offset(if (left) frame.left else frame.right, if (top) frame.top else frame.bottom)

    companion object {
        fun nearest(frame: Rect, point: Offset): Corner = entries.minBy { (it.of(frame) - point).getDistance() }
    }
}

/** Move o canto [corner] em frações da imagem, mantendo a moldura dentro da foto e com tamanho mínimo. */
private fun EditOperation.Crop.moved(corner: Corner, dx: Float, dy: Float): EditOperation.Crop {
    var left = x
    var top = y
    var right = x + w
    var bottom = y + h
    if (corner.left) {
        left = (left + dx).coerceIn(0f, right - MIN_FRACTION)
    } else {
        right =
            (right + dx).coerceIn(left + MIN_FRACTION, 1f)
    }
    if (corner.top) {
        top = (top + dy).coerceIn(0f, bottom - MIN_FRACTION)
    } else {
        bottom =
            (bottom + dy).coerceIn(top + MIN_FRACTION, 1f)
    }
    return EditOperation.Crop(left, top, right - left, bottom - top)
}

/** Lado mínimo do recorte: 10% da imagem. */
private const val MIN_FRACTION = 0.1f
