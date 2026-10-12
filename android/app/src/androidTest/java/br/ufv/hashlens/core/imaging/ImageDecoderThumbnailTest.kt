package br.ufv.hashlens.core.imaging

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Miniatura do acervo (T089): decodifica já reduzida, sem carregar a foto inteira, com o lado maior
 * entre [SIDE] e o dobro dele, e na proporção da foto. Usa o `BitmapFactory` real.
 */
@RunWith(AndroidJUnit4::class)
class ImageDecoderThumbnailTest {
    private val decoder = ImageDecoder()

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun fotoGrandeSaiReduzidaNaProporcao() {
        val thumbnail = decoder.decodeThumbnail(jpeg(4000, 3000), SIDE)

        thumbnail.width shouldBeGreaterThanOrEqual SIDE
        thumbnail.width shouldBeLessThan SIDE * 2
        (thumbnail.width * 3) shouldBe (thumbnail.height * 4)
    }

    @Test
    fun fotoPequenaNaoEAmpliada() {
        val thumbnail = decoder.decodeThumbnail(jpeg(300, 200), SIDE)

        thumbnail.width shouldBe 300
        thumbnail.height shouldBe 200
    }

    @Test
    fun arquivoQueNaoEImagemFalha() {
        shouldThrow<IllegalArgumentException> { decoder.decodeThumbnail("não é imagem".encodeToByteArray(), SIDE) }
    }

    private companion object {
        const val SIDE = 512
    }
}
