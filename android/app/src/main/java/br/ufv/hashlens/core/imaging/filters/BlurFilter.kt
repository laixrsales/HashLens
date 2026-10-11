package br.ufv.hashlens.core.imaging.filters

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Desfoque gaussiano com janela [kernel] × [kernel] (ímpar, 3..25); o desvio vem do tamanho da janela. */
class BlurFilter(private val kernel: Int) : ImageFilter {
    override fun apply(source: Mat): Mat = Mat().also {
        Imgproc.GaussianBlur(source, it, Size(kernel.toDouble(), kernel.toDouble()), 0.0)
    }
}
