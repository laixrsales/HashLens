package br.ufv.hashlens.benchmark

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.JpegEncoder
import io.kotest.assertions.withClue
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * T094: tempo do cálculo dos identificadores de uma foto (SHA-256 + pHash v1) no aparelho, para fotos
 * de 2, 8, 12 e 48 MP. SC-006: até 2 s para 12 MP em aparelho intermediário — o teste falha acima disso.
 *
 * Mede o mesmo que o app faz depois de gravar a foto: [Sha256Hasher] e [PerceptualHasher] sobre os bytes
 * do JPEG (o pHash decodifica com o OpenCV). Cada tamanho roda [WARMUP] vezes sem medir (JIT e caches)
 * e [RUNS] vezes medindo; vale a mediana.
 *
 * As fotos são sintéticas, geradas com o OpenCV em memória nativa (uma de 48 MP ocuparia ~192 MB como
 * `Bitmap`): ruído com semente fixa suavizado, que dá textura de foto e arquivos do tamanho de fotos
 * reais, codificado com a qualidade do app ([JpegEncoder.QUALITY]). O resultado vai para o log
 * (`HashingBenchmark`), para a saída do `am instrument -r` e para `hashing.csv` em
 * `/sdcard/Android/data/br.ufv.hashlens/files/benchmark/` (`adb pull`).
 */
@RunWith(AndroidJUnit4::class)
class HashingBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val sha256Hasher = Sha256Hasher()
    private val perceptualHasher = PerceptualHasher()

    /** Uma linha do resultado: medianas e máximos, em milissegundos. */
    private data class Result(
        val megapixels: Int,
        val width: Int,
        val height: Int,
        val fileBytes: Int,
        val sha256Median: Long,
        val pHashMedian: Long,
        val totalMedian: Long,
        val totalMax: Long
    )

    @Test
    fun identificadoresDe2_8_12e48Megapixels() {
        val results = SIZES.map { (megapixels, size) -> measure(megapixels, size.first, size.second) }
        report(results)

        val twelve = results.single { it.megapixels == 12 }
        withClue("SC-006: identificadores de uma foto de 12 MP em até 2 s (mediana de $RUNS execuções)") {
            twelve.totalMedian shouldBeLessThanOrEqual SC006_LIMIT_MS
        }
    }

    private fun measure(megapixels: Int, width: Int, height: Int): Result {
        val jpeg = syntheticPhoto(width, height)
        repeat(WARMUP) { identifiers(jpeg) }
        val runs = List(RUNS) { identifiers(jpeg) }
        val totals = runs.map { it.first + it.second }.sorted()
        return Result(
            megapixels = megapixels,
            width = width,
            height = height,
            fileBytes = jpeg.size,
            sha256Median = runs.map { it.first }.median(),
            pHashMedian = runs.map { it.second }.median(),
            totalMedian = totals.median(),
            totalMax = totals.last()
        )
    }

    /** Tempos do SHA-256 e do pHash, em ms. */
    private fun identifiers(jpeg: ByteArray): Pair<Long, Long> {
        val start = System.nanoTime()
        sha256Hasher.hash(jpeg.inputStream())
        val afterSha = System.nanoTime()
        perceptualHasher.hash(jpeg)
        val end = System.nanoTime()
        return (afterSha - start) / NANOS_PER_MS to (end - afterSha) / NANOS_PER_MS
    }

    private fun List<Long>.median(): Long = sorted()[size / 2]

    private fun syntheticPhoto(width: Int, height: Int): ByteArray {
        val image = Mat(height, width, CvType.CV_8UC3)
        val encoded = MatOfByte()
        try {
            Core.setRNGSeed(SEED)
            Core.randn(image, MEAN, STDDEV)
            Imgproc.GaussianBlur(image, image, Size(BLUR.toDouble(), BLUR.toDouble()), 0.0)
            val quality = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, JpegEncoder.QUALITY)
            check(Imgcodecs.imencode(".jpg", image, encoded, quality)) { "falha ao codificar a foto de teste" }
            return encoded.toArray()
        } finally {
            image.release()
            encoded.release()
        }
    }

    private fun report(results: List<Result>) {
        // SOC_MODEL só existe a partir do Android 12
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        val device = "${Build.MANUFACTURER} ${Build.MODEL} ($soc), Android ${Build.VERSION.RELEASE}"
        val csv = buildString {
            appendLine("# $device; mediana de $RUNS execuções após $WARMUP de aquecimento")
            appendLine(
                "megapixels,largura,altura,bytes_jpeg,sha256_mediana_ms,phash_mediana_ms,total_mediana_ms,total_max_ms"
            )
            results.forEach {
                appendLine(
                    "${it.megapixels},${it.width},${it.height},${it.fileBytes},${it.sha256Median}," +
                        "${it.pHashMedian},${it.totalMedian},${it.totalMax}"
                )
            }
        }
        Log.i(TAG, csv)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$csv") })
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "benchmark").apply { mkdirs() }
        File(dir, "hashing.csv").writeText(csv)
    }

    private companion object {
        const val TAG = "HashingBenchmark"
        const val WARMUP = 2
        const val RUNS = 7
        const val SC006_LIMIT_MS = 2_000L
        const val NANOS_PER_MS = 1_000_000L
        const val SEED = 42
        const val MEAN = 128.0
        const val STDDEV = 48.0
        const val BLUR = 5

        /** Megapixels → largura × altura (4:3, como a câmera). */
        val SIZES = listOf(
            2 to (1632 to 1224),
            8 to (3264 to 2448),
            12 to (4000 to 3000),
            48 to (8000 to 6000)
        )
    }
}
