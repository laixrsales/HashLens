package br.ufv.hashlens.core.imaging.filters

import org.opencv.core.Mat

/** Brilho: soma [delta] (−100..+100) a cada canal, saturando em 0 e 255. */
class BrightnessFilter(private val delta: Int) : ImageFilter {
    override fun apply(source: Mat): Mat = Mat().also { source.convertTo(it, -1, 1.0, delta.toDouble()) }
}
