package br.ufv.hashlens.core.imaging

import android.graphics.Bitmap
import br.ufv.hashlens.domain.model.EditOperation
import java.time.ZonedDateTime
import javax.inject.Inject
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Arquivo final de uma edição: decodifica a imagem do pai já em pé (EXIF, research R10), aplica as
 * operações em resolução cheia ([FilterPipeline]) e codifica o JPEG como na captura (hashing-spec §5).
 */
class EditRenderer @Inject constructor(
    private val decoder: ImageDecoder,
    private val pipeline: FilterPipeline,
    private val encoder: JpegEncoder
) {
    /**
     * @param source bytes exatos do arquivo do pai.
     * @param editedAt momento da edição, gravado no EXIF antes do cálculo dos hashes.
     * @throws IllegalArgumentException se [source] não decodificar ou alguma operação for inválida.
     */
    fun render(source: ByteArray, operations: List<EditOperation>, editedAt: ZonedDateTime): ByteArray {
        val original = fromBitmap(decoder.decode(source))
        val result = try {
            pipeline.apply(original, operations)
        } finally {
            original.release()
        }
        val edited = toBitmap(result)
        try {
            return encoder.encode(edited, rotationDegrees = 0, capturedAt = editedAt)
        } finally {
            edited.recycle()
        }
    }

    /** `Bitmap` ARGB → `Mat` BGR de 8 bits; libera o bitmap. */
    private fun fromBitmap(bitmap: Bitmap): Mat {
        val rgba = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            return Mat().also { Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2BGR) }
        } finally {
            rgba.release()
            bitmap.recycle()
        }
    }

    /** `Mat` BGR de 8 bits → `Bitmap` ARGB opaco; libera o `Mat`. */
    private fun toBitmap(bgr: Mat): Bitmap {
        val rgba = Mat()
        try {
            Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA)
            return Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(rgba, it)
            }
        } finally {
            rgba.release()
            bgr.release()
        }
    }
}
