package br.ufv.hashlens.core.imaging.filters

import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Preto e branco: luminância (BT.601) repetida nos três canais. */
object GrayscaleFilter : ImageFilter {
    override fun apply(source: Mat): Mat = withTemporaries(1) { (gray) ->
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY)
        Mat().also { Imgproc.cvtColor(gray, it, Imgproc.COLOR_GRAY2BGR) }
    }
}
