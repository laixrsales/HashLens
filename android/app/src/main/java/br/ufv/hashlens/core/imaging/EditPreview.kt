package br.ufv.hashlens.core.imaging

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import br.ufv.hashlens.domain.model.EditOperation
import java.io.Closeable
import javax.inject.Inject
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Prévia do editor (plan "Edição"): a foto decodificada uma vez, já em pé, reduzida a [MAX_SIDE]
 * pixels no lado maior; cada mudança nas operações é renderizada sobre essa cópia pelo
 * [FilterPipeline]. O arquivo registrado sai do [EditRenderer], em resolução cheia.
 */
class EditPreviewFactory @Inject constructor(private val decoder: ImageDecoder, private val pipeline: FilterPipeline) {
    /** @throws IllegalArgumentException se [bytes] não decodificar. */
    fun open(bytes: ByteArray): EditPreview {
        val bitmap = decoder.decode(bytes)
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            return EditPreview(pipeline.preview(bgr, emptyList(), MAX_SIDE), pipeline)
        } finally {
            rgba.release()
            bgr.release()
            bitmap.recycle()
        }
    }

    private companion object {
        /** Nítido na tela de um telefone e rápido o bastante para acompanhar o controle deslizante. */
        const val MAX_SIDE = 1280
    }
}

/**
 * Foto reduzida do editor; libere com [close] quando a tela fechar. [render] e [close] são
 * sincronizados: a tela pode fechar enquanto uma prévia ainda está sendo calculada.
 */
class EditPreview internal constructor(private val base: Mat, private val pipeline: FilterPipeline) : Closeable {
    private var closed = false

    /** A foto sem alterações (antes/depois). */
    val original: ImageBitmap = toImage(base)

    /** A foto com [operations] aplicadas, na ordem; `null` depois de [close]. Chamar fora da thread principal. */
    @Synchronized
    fun render(operations: List<EditOperation>): ImageBitmap? = when {
        closed -> null
        operations.isEmpty() -> original
        else -> applied(operations)
    }

    private fun applied(operations: List<EditOperation>): ImageBitmap {
        val result = pipeline.apply(base, operations)
        try {
            return toImage(result)
        } finally {
            result.release()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        base.release()
    }

    private fun toImage(bgr: Mat): ImageBitmap {
        val rgba = Mat()
        try {
            Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA)
            val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bitmap)
            return bitmap.asImageBitmap()
        } finally {
            rgba.release()
        }
    }
}
