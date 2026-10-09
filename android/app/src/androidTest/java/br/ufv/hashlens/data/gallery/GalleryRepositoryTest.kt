package br.ufv.hashlens.data.gallery

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T040: gravação na galeria e releitura (research R8). O hash é calculado sobre os bytes da
 * galeria, então a releitura tem de devolver exatamente o que foi gravado.
 *
 * Instrumentado porque usa o MediaStore do aparelho. Apaga as fotos gravadas ao fim de cada teste.
 */
@RunWith(AndroidJUnit4::class)
class GalleryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver: ContentResolver = context.contentResolver
    private val repository = GalleryRepository(context)
    private val saved = mutableListOf<Uri>()

    private fun golden(file: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open("golden/$file").use { it.readBytes() }

    @After
    fun deleteSavedPhotos() {
        saved.forEach { resolver.delete(it, null, null) }
    }

    @Test
    fun bytesRelidosSaoIguaisAosGravados() = runBlocking {
        val bytes = golden(ORIGINAL)

        val uri = repository.save(bytes, "hashlens_teste_releitura.jpg").also { saved += it }

        repository.read(uri).toHexString() shouldBe bytes.toHexString()
    }

    @Test
    fun releituraNaoDependeDoConteudoDaImagem() = runBlocking {
        // Bytes que não formam um JPEG válido: a galeria não pode recodificar nem alterar nada
        val bytes = golden(ORIGINAL).copyOf(ARBITRARY_PREFIX) + byteArrayOf(0, 1, 2, 3)

        val uri = repository.save(bytes, "hashlens_teste_bytes.jpg").also { saved += it }

        repository.read(uri).toHexString() shouldBe bytes.toHexString()
    }

    @Test
    fun gravaComoJpegNaPastaDoHashLens() = runBlocking {
        val uri = repository.save(golden(ORIGINAL), "hashlens_teste_pasta.jpg").also { saved += it }

        resolver.getType(uri) shouldBe "image/jpeg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.query(uri, arrayOf(MediaStore.Images.Media.RELATIVE_PATH), null, null, null).use { cursor ->
                requireNotNull(cursor).moveToFirst() shouldBe true
                cursor.getString(0) shouldStartWith "Pictures/HashLens"
            }
        }
    }

    private companion object {
        const val ORIGINAL = "originais/20261006_205911.jpg"
        const val ARBITRARY_PREFIX = 1024
    }
}
