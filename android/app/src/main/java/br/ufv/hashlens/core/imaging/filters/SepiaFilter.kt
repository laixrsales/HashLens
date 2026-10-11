package br.ufv.hashlens.core.imaging.filters

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

/** Sépia: matriz clássica de tons quentes aplicada a cada pixel, saturando em 255. */
object SepiaFilter : ImageFilter {
    override fun apply(source: Mat): Mat {
        val kernel = Mat(SIZE, SIZE, CvType.CV_32F).apply { put(0, 0, *COEFFICIENTS) }
        try {
            return Mat().also { Core.transform(source, it, kernel) }
        } finally {
            kernel.release()
        }
    }

    private const val SIZE = 3

    // Linhas: canal de saída B, G, R; colunas: canal de entrada B, G, R
    private val COEFFICIENTS = doubleArrayOf(
        0.131, 0.534, 0.272,
        0.168, 0.686, 0.349,
        0.189, 0.769, 0.393
    )
}
