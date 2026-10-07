package br.ufv.hashlens.core.hashing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.domain.model.PHash
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T013: hashing-spec §2 (pHash v1).
 *
 * Instrumentado porque o pHash usa o OpenCV nativo do SDK Android, que não carrega na JVM do
 * computador. As imagens golden vêm de `src/test/resources/golden/`, expostas como assets do
 * teste em `app/build.gradle.kts`.
 */
@RunWith(AndroidJUnit4::class)
class PerceptualHasherTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val hasher = PerceptualHasher()

    private val expected: Map<String, String> by lazy {
        val files = JSONObject(read("expected.json").decodeToString()).getJSONObject("arquivos")
        files.keys().asSequence().associateWith { files.getJSONObject(it).getString("phash") }
    }

    private fun read(file: String): ByteArray = assets.open("golden/$file").use { it.readBytes() }

    private fun hash(file: String): PHash = hasher.hash(read(file))

    @Test
    fun arquivosGoldenBatemComExpectedJson() {
        assertSoftly {
            expected.forEach { (file, pHash) ->
                withClue(file) { hash(file).hex shouldBe pHash }
            }
        }
    }

    @Test
    fun resultadoIgualEm100Execucoes() {
        val bytes = read(COMPRESSED)

        val results = List(100) { hasher.hash(bytes) }.toSet()

        results shouldHaveSize 1
        results.single().hex shouldBe expected.getValue(COMPRESSED)
    }

    @Test
    fun orientacaoExifEhAplicadaAntesDoHash() {
        // Mesmos pixels; só a tag Orientation muda (6 no original, 1 na variação)
        val withRotation = hash(ORIGINAL)
        val withoutRotation = hash(EXIF_ORIENTATION_1)

        withRotation shouldNotBe withoutRotation
        withRotation.hex shouldBe expected.getValue(ORIGINAL)
        withoutRotation.hex shouldBe expected.getValue(EXIF_ORIENTATION_1)
    }

    @Test
    fun formatoHexTem16CaracteresMinusculos() {
        expected.keys.forEach { file -> hash(file).hex shouldMatch HEX_16 }
        PHash(1uL).hex shouldBe "0000000000000001"
        PHash(ULong.MAX_VALUE).hex shouldBe "ffffffffffffffff"
        PHash.fromHex("9e4c398ab9d93331") shouldBe PHash(0x9e4c398ab9d93331uL)
    }

    private companion object {
        const val ORIGINAL = "originais/20261006_205911.jpg"
        const val EXIF_ORIENTATION_1 = "orientacao_exif_1.jpg"
        const val COMPRESSED = "comprimida_q70.jpg"
        val HEX_16 = Regex("[0-9a-f]{16}")
    }
}
