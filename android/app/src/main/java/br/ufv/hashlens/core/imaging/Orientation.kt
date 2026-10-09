package br.ufv.hashlens.core.imaging

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
private const val THREE_QUARTER_TURN = 270f

/** Rotação no sentido horário seguida, ou não, de espelhamento horizontal. */
private data class Transform(val degrees: Float, val mirrored: Boolean)

/** Transformação que leva os pixels gravados à orientação de exibição, por tag EXIF `Orientation` (2 a 8). */
private val EXIF_TRANSFORMS = mapOf(
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL to Transform(0f, mirrored = true),
    ExifInterface.ORIENTATION_ROTATE_180 to Transform(HALF_TURN, mirrored = false),
    ExifInterface.ORIENTATION_FLIP_VERTICAL to Transform(HALF_TURN, mirrored = true),
    ExifInterface.ORIENTATION_TRANSPOSE to Transform(QUARTER_TURN, mirrored = true),
    ExifInterface.ORIENTATION_ROTATE_90 to Transform(QUARTER_TURN, mirrored = false),
    ExifInterface.ORIENTATION_TRANSVERSE to Transform(THREE_QUARTER_TURN, mirrored = true),
    ExifInterface.ORIENTATION_ROTATE_270 to Transform(THREE_QUARTER_TURN, mirrored = false)
)

/** Matriz da orientação EXIF; `null` quando a imagem já está em pé (1) ou a tag é desconhecida. */
internal fun exifOrientationMatrix(orientation: Int): Matrix? = EXIF_TRANSFORMS[orientation]?.let { transform ->
    Matrix().apply {
        setRotate(transform.degrees)
        if (transform.mirrored) postScale(-1f, 1f)
    }
}

/** Aplica [matrix] criando um bitmap novo; sem matriz, devolve o próprio bitmap. */
internal fun Bitmap.transformed(matrix: Matrix?): Bitmap =
    if (matrix == null) this else Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
