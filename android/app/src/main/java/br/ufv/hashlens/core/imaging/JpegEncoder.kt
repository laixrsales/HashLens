package br.ufv.hashlens.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import br.ufv.hashlens.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Gera os bytes JPEG finais de uma captura ou edição, já com EXIF (research R8 e R10).
 *
 * Os pixels são girados fisicamente e `Orientation` é gravado como 1, para que o arquivo tenha a
 * mesma aparência em qualquer visualizador. O arquivo nasce de um bitmap, então não carrega GPS
 * nem outros metadados da câmera (Princípio VI): só `DateTimeOriginal`, `OffsetTimeOriginal` e
 * `Software`.
 */
class JpegEncoder @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * @param rotationDegrees rotação no sentido horário que deixa a imagem em pé (0, 90, 180 ou 270),
     *   como a informada pelo CameraX.
     * @param capturedAt momento da captura, gravado com o fuso.
     */
    fun encode(bitmap: Bitmap, rotationDegrees: Int, capturedAt: ZonedDateTime): ByteArray {
        require(rotationDegrees in VALID_ROTATIONS) { "rotação inválida: $rotationDegrees" }
        val matrix = if (rotationDegrees == 0) null else Matrix().apply { setRotate(rotationDegrees.toFloat()) }
        val upright = bitmap.transformed(matrix)

        // ExifInterface só grava em arquivo; o temporário fica no cache privado e é apagado em seguida
        val file = File.createTempFile("encode-", ".jpg", context.cacheDir)
        try {
            file.outputStream().use { out ->
                check(upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) { "falha ao codificar JPEG" }
            }
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, capturedAt.format(EXIF_DATE_TIME))
                setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, capturedAt.format(EXIF_OFFSET))
                setAttribute(ExifInterface.TAG_SOFTWARE, SOFTWARE)
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            file.delete()
            if (upright !== bitmap) upright.recycle()
        }
    }

    companion object {
        const val QUALITY = 95
        const val SOFTWARE = "HashLens ${BuildConfig.VERSION_NAME}"

        private val VALID_ROTATIONS = setOf(0, 90, 180, 270)
        private val EXIF_DATE_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
        private val EXIF_OFFSET = DateTimeFormatter.ofPattern("xxx")
    }
}
