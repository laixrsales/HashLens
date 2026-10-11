package br.ufv.hashlens.core.imaging

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.domain.model.EditOperation
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.ByteArrayInputStream
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arquivo final da edição (T070): decodifica o pai já em pé, aplica as operações em resolução cheia
 * e codifica o JPEG como na captura (hashing-spec §5). Usa a imagem golden de `src/test/resources`.
 */
@RunWith(AndroidJUnit4::class)
class EditRendererTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val renderer = EditRenderer(ImageDecoder(), FilterPipeline(), JpegEncoder(instrumentation.targetContext))
    private val source = instrumentation.context.assets.open("golden/edicao_leve.jpg").use { it.readBytes() }

    private fun size(jpeg: ByteArray) = BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, this)
        outWidth to outHeight
    }

    @Test
    fun aplicaAsOperacoesEmResolucaoCheia() {
        val (width, height) = size(source)

        val rotated = renderer.render(source, listOf(EditOperation.Rotate90(1)), EDITED_AT)
        val cropped = renderer.render(source, listOf(EditOperation.Crop(0f, 0f, 0.5f, 0.5f)), EDITED_AT)

        size(rotated) shouldBe (height to width)
        size(cropped) shouldBe (Math.round(width * 0.5f) to Math.round(height * 0.5f))
    }

    @Test
    fun gravaOExifDaSaidaComoNaCaptura() {
        val edited = renderer.render(source, listOf(EditOperation.Grayscale), EDITED_AT)

        ExifInterface(ByteArrayInputStream(edited)).run {
            getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) shouldBe ExifInterface.ORIENTATION_NORMAL
            getAttribute(ExifInterface.TAG_SOFTWARE) shouldBe JpegEncoder.SOFTWARE
            getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) shouldBe "2026:10:11 15:00:00"
            getAttribute(ExifInterface.TAG_GPS_LATITUDE) shouldBe null
        }
    }

    @Test
    fun mesmaEdicaoNoMesmoInstanteGeraOsMesmosBytes() {
        val operations = listOf(EditOperation.Brightness(30), EditOperation.Sepia)

        val first = renderer.render(source, operations, EDITED_AT)
        val second = renderer.render(source, operations, EDITED_AT)

        first.contentEquals(second) shouldBe true
        first.contentEquals(source) shouldNotBe true
    }

    private companion object {
        val EDITED_AT: ZonedDateTime = ZonedDateTime.parse("2026-10-11T15:00:00-03:00")
    }
}
