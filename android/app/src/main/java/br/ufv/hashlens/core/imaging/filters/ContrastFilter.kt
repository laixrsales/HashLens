package br.ufv.hashlens.core.imaging.filters

import org.opencv.core.Mat

/**
 * Contraste: multiplica a distância de cada canal ao meio da escala (128) por [factor]
 * (0.50..2.00), saturando em 0 e 255. O cinza médio fica onde está.
 */
class ContrastFilter(private val factor: Float) : ImageFilter {
    override fun apply(source: Mat): Mat = Mat().also {
        val alpha = factor.toDouble()
        source.convertTo(it, -1, alpha, MID_LEVEL * (1 - alpha))
    }

    private companion object {
        const val MID_LEVEL = 128.0
    }
}
