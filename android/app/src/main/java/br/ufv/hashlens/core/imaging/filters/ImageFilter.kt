package br.ufv.hashlens.core.imaging.filters

import br.ufv.hashlens.domain.model.EditOperation
import org.opencv.core.Mat

/**
 * Filtro da edição (hashing-spec §4) sobre uma imagem BGR de 8 bits (`CV_8UC3`).
 *
 * Devolve sempre uma imagem nova, também BGR de 8 bits, sem alterar a entrada: os filtros se
 * encadeiam na ordem das operações e o resultado vai direto para o JPEG. Quem chama libera as duas.
 */
fun interface ImageFilter {
    fun apply(source: Mat): Mat
}

/** O filtro que aplica esta operação. */
fun EditOperation.toFilter(): ImageFilter = when (this) {
    is EditOperation.Brightness -> BrightnessFilter(delta)
    is EditOperation.Contrast -> ContrastFilter(factor)
    EditOperation.Grayscale -> GrayscaleFilter
    EditOperation.Sepia -> SepiaFilter
    is EditOperation.Blur -> BlurFilter(kernel)
    EditOperation.Sharpen -> SharpenFilter
    is EditOperation.Edges -> EdgesFilter(low, high)
    is EditOperation.Crop -> CropFilter(x, y, w, h)
    is EditOperation.Rotate90 -> Rotate90Filter(turns)
}

/** Executa [block] com Mats temporários e os libera no fim, mesmo em caso de erro. */
internal inline fun <T> withTemporaries(count: Int, block: (List<Mat>) -> T): T {
    val temporaries = List(count) { Mat() }
    try {
        return block(temporaries)
    } finally {
        temporaries.forEach(Mat::release)
    }
}
