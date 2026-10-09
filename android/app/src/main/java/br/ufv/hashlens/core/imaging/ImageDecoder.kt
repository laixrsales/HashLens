package br.ufv.hashlens.core.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import javax.inject.Inject

/**
 * Decodifica uma imagem para exibição e edição, já na orientação indicada pelo EXIF (research R10).
 *
 * O pHash não passa por aqui: [br.ufv.hashlens.core.hashing.PerceptualHasher] decodifica com o
 * OpenCV, como a implementação de referência (research R26).
 */
class ImageDecoder @Inject constructor() {
    /** @throws IllegalArgumentException se [bytes] não for uma imagem decodificável. */
    fun decode(bytes: ByteArray): Bitmap {
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "arquivo não é uma imagem decodificável"
        }
        val upright = decoded.transformed(exifOrientationMatrix(exifOrientation(bytes)))
        if (upright !== decoded) decoded.recycle()
        return upright
    }

    private fun exifOrientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
}
