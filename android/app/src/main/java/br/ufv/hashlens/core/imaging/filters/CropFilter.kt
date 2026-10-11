package br.ufv.hashlens.core.imaging.filters

import kotlin.math.roundToInt
import org.opencv.core.Mat
import org.opencv.core.Rect

/**
 * Recorte em frações da imagem (0..1): origem ([x], [y]) e tamanho ([w], [h]), arredondados ao
 * pixel mais próximo e limitados à imagem, com pelo menos 1 pixel de cada lado.
 */
class CropFilter(private val x: Float, private val y: Float, private val w: Float, private val h: Float) : ImageFilter {
    override fun apply(source: Mat): Mat {
        val cols = source.cols()
        val rows = source.rows()
        val left = (x * cols).roundToInt().coerceIn(0, cols - 1)
        val top = (y * rows).roundToInt().coerceIn(0, rows - 1)
        val width = (w * cols).roundToInt().coerceIn(1, cols - left)
        val height = (h * rows).roundToInt().coerceIn(1, rows - top)
        val region = source.submat(Rect(left, top, width, height))
        try {
            // Cópia própria: a submatriz aponta para os pixels da entrada
            return region.clone()
        } finally {
            region.release()
        }
    }
}
