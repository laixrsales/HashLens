package br.ufv.hashlens.core.imaging

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.domain.model.EditOperation
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.math.max
import org.junit.Test
import org.junit.runner.RunWith

/** Prévia do Editor (T073): foto reduzida, operações renderizadas sobre ela e liberação ao fechar. */
@RunWith(AndroidJUnit4::class)
class EditPreviewTest {
    private val factory = EditPreviewFactory(ImageDecoder(), FilterPipeline())
    private val source = InstrumentationRegistry.getInstrumentation().context.assets
        .open("golden/edicao_leve.jpg").use { it.readBytes() }

    @Test
    fun originalFicaReduzidaComAsProporcoesDoArquivo() {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            .also { BitmapFactory.decodeByteArray(source, 0, source.size, it) }

        factory.open(source).use { preview ->
            val original = preview.original
            max(original.width, original.height) shouldBeLessThanOrEqual 1280
            // Mesma proporção do arquivo, com tolerância de arredondamento de 1 pixel
            val expectedHeight = original.width.toLong() * bounds.outHeight / bounds.outWidth
            (original.height - expectedHeight.toInt()).let { if (it < 0) -it else it } shouldBeLessThanOrEqual 1
        }
    }

    @Test
    fun renderizaAsOperacoesSobreAFotoReduzida() {
        factory.open(source).use { preview ->
            val original = preview.original

            val rotated = preview.render(listOf(EditOperation.Grayscale, EditOperation.Rotate90(1))).shouldNotBeNull()

            (rotated.width to rotated.height) shouldBe (original.height to original.width)
            preview.render(emptyList()) shouldBeSameInstanceAs original
        }
    }

    @Test
    fun depoisDeFecharNaoRenderiza() {
        val preview = factory.open(source)

        preview.close()
        preview.close()

        preview.render(listOf(EditOperation.Sepia)).shouldBeNull()
    }
}
