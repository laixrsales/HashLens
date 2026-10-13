package br.ufv.hashlens.evaluation

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.imaging.ImageDecoder
import br.ufv.hashlens.domain.model.PHash
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T095: avaliação de recompressão para o SC-004 ("ao menos 90% das cópias recomprimidas com qualidade
 * JPEG ≥ 70 são reconhecidas como Correspondência visual"; research R11).
 *
 * A Correspondência visual consulta o contrato por igualdade exata de pHash, então uma cópia é
 * reconhecida quando o pHash dela é igual, bit a bit, ao do arquivo registrado. Para cada imagem golden,
 * a cópia é feita como um app de mensagens costuma fazer: decodifica já na orientação do EXIF
 * ([ImageDecoder]), recodifica em JPEG com o codificador do Android, sem redimensionar e sem EXIF, nas
 * qualidades 70, 75, 80, 85, 90 e 95.
 *
 * O teste falha se a taxa global ficar abaixo de 90%. A tabela imagem × qualidade (com a distância de
 * Hamming de cada cópia) vai para o log, para a saída do `am instrument -r` e para
 * `sc-004-recompressao.csv` em `/sdcard/Android/data/br.ufv.hashlens/files/evaluation/` (`adb pull`).
 */
@RunWith(AndroidJUnit4::class)
class RecompressionMatchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val decoder = ImageDecoder()
    private val hasher = PerceptualHasher()

    private data class Copy(
        val image: String,
        val quality: Int,
        val original: PHash,
        val copy: PHash,
        val jpegBytes: Int
    ) {
        val distance: Int get() = java.lang.Long.bitCount((original.value xor copy.value).toLong())
        val matches: Boolean get() = distance == 0
    }

    @Test
    fun copiasRecomprimidasMantemOPHashEmAoMenos90PorCento() {
        val copies = IMAGES.flatMap { image -> copiesOf(image) }
        report(copies)

        val rate = copies.count { it.matches }.toDouble() / copies.size
        withClue("SC-004: cópias com o mesmo pHash do registrado (${copies.count { it.matches }} de ${copies.size})") {
            rate shouldBeGreaterThanOrEqual MIN_RATE
        }
    }

    private fun copiesOf(image: String): List<Copy> {
        val bytes = assets.open("golden/$image").use { it.readBytes() }
        val original = hasher.hash(bytes)
        val upright = decoder.decode(bytes)
        try {
            return QUALITIES.map { quality ->
                val copy = ByteArrayOutputStream().also {
                    check(upright.compress(Bitmap.CompressFormat.JPEG, quality, it))
                }
                    .toByteArray()
                Copy(image, quality, original, hasher.hash(copy), copy.size)
            }
        } finally {
            upright.recycle()
        }
    }

    private fun report(copies: List<Copy>) {
        val csv = buildString {
            appendLine("imagem,qualidade,phash_original,phash_copia,hamming,igual,bytes_copia")
            copies.forEach {
                appendLine(
                    "${it.image},${it.quality},${it.original.hex},${it.copy.hex}," +
                        "${it.distance},${it.matches},${it.jpegBytes}"
                )
            }
        }
        Log.i(TAG, csv)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$csv") })
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "evaluation").apply { mkdirs() }
        File(dir, "sc-004-recompressao.csv").writeText(csv)
    }

    private companion object {
        const val TAG = "RecompressionMatch"
        const val MIN_RATE = 0.90

        /** Qualidades JPEG do SC-004 (≥ 70). */
        val QUALITIES = listOf(70, 75, 80, 85, 90, 95)

        /** As imagens golden (src/test/resources/golden, expostas como assets do teste). */
        val IMAGES = listOf(
            "originais/20261006_205911.jpg",
            "originais/20261006_205922.jpg",
            "edicao_leve.jpg",
            "comprimida_q70.jpg",
            "preto_branco.jpg",
            "orientacao_exif_1.jpg"
        )
    }
}
