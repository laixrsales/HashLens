package br.ufv.hashlens.core.hashing

import androidx.exifinterface.media.ExifInterface
import br.ufv.hashlens.domain.model.PHash
import java.io.ByteArrayInputStream
import javax.inject.Inject
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * Hash perceptual v1 (hashing-spec §2). Qualquer mudança aqui altera os pHashes já registrados.
 *
 * A decodificação usa o próprio OpenCV (`Imgcodecs.imdecode`), o mesmo decodificador da
 * implementação de referência (`tools/golden/gerar_golden.py`), em vez do `BitmapFactory`.
 */
class PerceptualHasher @Inject constructor() {
    init {
        check(opencvLoaded) { "OpenCV nativo não carregou" }
    }

    /** @throws IllegalArgumentException se [bytes] não for uma imagem decodificável. */
    fun hash(bytes: ByteArray): PHash {
        val resources = mutableListOf<Mat>()
        fun <T : Mat> T.tracked(): T = also { resources += it }

        try {
            val image = decode(bytes, resources)
            // Passos 2 a 5: cinza 8 bits → 32×32 (INTER_AREA) → float 0..255 → DCT 2D
            val gray = Mat().tracked().also { Imgproc.cvtColor(image, it, Imgproc.COLOR_BGR2GRAY) }
            val small = Mat().tracked().also {
                Imgproc.resize(gray, it, Size(DCT_SIZE.toDouble(), DCT_SIZE.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            }
            val floats = Mat().tracked().also { small.convertTo(it, CvType.CV_32F) }
            val dct = Mat().tracked().also { Core.dct(floats, it) }
            return PHash(bitsAboveMedian(lowFrequencyBlock(dct)))
        } finally {
            resources.forEach(Mat::release)
        }
    }

    /** Passo 1: decodifica (BGR, 8 bits) e aplica a orientação EXIF, se houver. */
    private fun decode(bytes: ByteArray, resources: MutableList<Mat>): Mat {
        val encoded = MatOfByte(*bytes).also { resources += it }
        val decoded = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR or Imgcodecs.IMREAD_IGNORE_ORIENTATION)
            .also { resources += it }
        require(!decoded.empty()) { "arquivo não é uma imagem decodificável" }
        return applyOrientation(decoded, exifOrientation(bytes), resources)
    }

    private fun exifOrientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyOrientation(image: Mat, orientation: Int, resources: MutableList<Mat>): Mat {
        val mirrored = orientation in MIRRORED
        val rotation = ROTATIONS[orientation]
        if (!mirrored && rotation == null) return image

        var result = image
        if (mirrored) {
            result = Mat().also {
                Core.flip(result, it, HORIZONTAL_FLIP)
                resources += it
            }
        }
        if (rotation != null) {
            val source = result
            result = Mat().also {
                Core.rotate(source, it, rotation)
                resources += it
            }
        }
        return result
    }

    /** Passo 6: bloco 8×8 superior esquerdo, linha a linha, incluindo o termo DC. */
    private fun lowFrequencyBlock(dct: Mat): FloatArray {
        val all = FloatArray(DCT_SIZE * DCT_SIZE).also { dct.get(0, 0, it) }
        return FloatArray(BLOCK_SIZE * BLOCK_SIZE) { i -> all[(i / BLOCK_SIZE) * DCT_SIZE + i % BLOCK_SIZE] }
    }

    /** Passos 7 a 9: bit = valor > mediana; o primeiro valor vira o bit mais significativo. */
    private fun bitsAboveMedian(values: FloatArray): ULong {
        val sorted = values.sortedArray()
        val middle = sorted.size / 2
        val median = (sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2
        return values.fold(0uL) { acc, v -> (acc shl 1) or if (v > median) 1uL else 0uL }
    }

    private companion object {
        const val DCT_SIZE = 32
        const val BLOCK_SIZE = 8
        const val HORIZONTAL_FLIP = 1

        val opencvLoaded: Boolean by lazy { OpenCVLoader.initLocal() }

        /** Orientações EXIF com espelhamento horizontal antes da rotação. */
        val MIRRORED = setOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE
        )

        /** Rotação aplicada depois do espelhamento, por orientação EXIF (2 a 8). */
        val ROTATIONS = mapOf(
            ExifInterface.ORIENTATION_ROTATE_180 to Core.ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_VERTICAL to Core.ROTATE_180,
            ExifInterface.ORIENTATION_TRANSPOSE to Core.ROTATE_90_COUNTERCLOCKWISE,
            ExifInterface.ORIENTATION_ROTATE_90 to Core.ROTATE_90_CLOCKWISE,
            ExifInterface.ORIENTATION_TRANSVERSE to Core.ROTATE_90_CLOCKWISE,
            ExifInterface.ORIENTATION_ROTATE_270 to Core.ROTATE_90_COUNTERCLOCKWISE
        )
    }
}
