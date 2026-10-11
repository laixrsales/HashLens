package br.ufv.hashlens.core.imaging

import br.ufv.hashlens.core.imaging.filters.toFilter
import br.ufv.hashlens.domain.model.EditOperation
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Aplica as operações da edição, na ordem, sobre uma imagem BGR de 8 bits (hashing-spec §4).
 *
 * A prévia do editor roda sobre uma cópia reduzida ([preview]); o arquivo registrado sai da
 * imagem em resolução cheia ([apply]). As duas devolvem uma imagem nova e não alteram a entrada.
 */
class FilterPipeline @Inject constructor() {
    init {
        check(opencvLoaded) { "OpenCV nativo não carregou" }
    }

    /** Resultado em resolução cheia; sem operações, uma cópia da entrada. */
    fun apply(source: Mat, operations: List<EditOperation>): Mat {
        var current = source.clone()
        var finished = false
        try {
            operations.forEach { operation ->
                val next = operation.toFilter().apply(current)
                current.release()
                current = next
            }
            finished = true
            return current
        } finally {
            // Em falha no meio da lista, o resultado parcial não sai daqui
            if (!finished) current.release()
        }
    }

    /**
     * Prévia com o lado maior da entrada reduzido a [maxSide] antes dos filtros (nunca ampliada).
     * Recorte e rotação usam frações e voltas, então a prévia tem as proporções do resultado final.
     */
    fun preview(source: Mat, operations: List<EditOperation>, maxSide: Int): Mat {
        val scale = maxSide.toDouble() / max(source.cols(), source.rows())
        if (scale >= 1.0) return apply(source, operations)
        val reduced = Mat()
        try {
            val size =
                Size((source.cols() * scale).roundToInt().toDouble(), (source.rows() * scale).roundToInt().toDouble())
            Imgproc.resize(source, reduced, size, 0.0, 0.0, Imgproc.INTER_AREA)
            return apply(reduced, operations)
        } finally {
            reduced.release()
        }
    }

    private companion object {
        val opencvLoaded: Boolean by lazy { OpenCVLoader.initLocal() }
    }
}
