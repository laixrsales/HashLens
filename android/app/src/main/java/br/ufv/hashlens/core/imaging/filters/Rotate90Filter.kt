package br.ufv.hashlens.core.imaging.filters

import org.opencv.core.Core
import org.opencv.core.Mat

/** Rotação em [turns] voltas de 90° no sentido horário (1..3). */
class Rotate90Filter(private val turns: Int) : ImageFilter {
    override fun apply(source: Mat): Mat {
        val code = when (turns) {
            QUARTER -> Core.ROTATE_90_CLOCKWISE
            HALF -> Core.ROTATE_180
            THREE_QUARTERS -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> throw IllegalArgumentException("voltas de 90° devem ser 1, 2 ou 3: $turns")
        }
        return Mat().also { Core.rotate(source, it, code) }
    }

    private companion object {
        const val QUARTER = 1
        const val HALF = 2
        const val THREE_QUARTERS = 3
    }
}
