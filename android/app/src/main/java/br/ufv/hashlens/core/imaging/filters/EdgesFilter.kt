package br.ufv.hashlens.core.imaging.filters

import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Bordas: Canny sobre a luminância, com limiares [low] e [high] (0..255); bordas brancas sobre preto. */
class EdgesFilter(private val low: Int, private val high: Int) : ImageFilter {
    override fun apply(source: Mat): Mat = withTemporaries(2) { (gray, edges) ->
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.Canny(gray, edges, low.toDouble(), high.toDouble())
        Mat().also { Imgproc.cvtColor(edges, it, Imgproc.COLOR_GRAY2BGR) }
    }
}
