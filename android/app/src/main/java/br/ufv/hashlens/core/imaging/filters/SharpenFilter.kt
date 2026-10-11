package br.ufv.hashlens.core.imaging.filters

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Nitidez: realce de bordas com o núcleo laplaciano 3 × 3 de centro 5. */
object SharpenFilter : ImageFilter {
    override fun apply(source: Mat): Mat {
        val kernel = Mat(SIZE, SIZE, CvType.CV_32F).apply { put(0, 0, *COEFFICIENTS) }
        try {
            return Mat().also { Imgproc.filter2D(source, it, -1, kernel) }
        } finally {
            kernel.release()
        }
    }

    private const val SIZE = 3

    private val COEFFICIENTS = doubleArrayOf(
        0.0, -1.0, 0.0,
        -1.0, 5.0, -1.0,
        0.0, -1.0, 0.0
    )
}
