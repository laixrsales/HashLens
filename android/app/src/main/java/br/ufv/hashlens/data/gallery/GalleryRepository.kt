package br.ufv.hashlens.data.gallery

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import br.ufv.hashlens.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fotos do HashLens na galeria (MediaStore, `Pictures/HashLens`).
 *
 * Os bytes são gravados sem recodificação, e o SHA-256 do registro é calculado sobre o que
 * [read] devolve depois da gravação (research R8, Constituição I).
 *
 * No Android 8 e 9 a gravação exige `WRITE_EXTERNAL_STORAGE` concedida; a partir do 10 não.
 */
@Singleton
class GalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val resolver get() = context.contentResolver

    /**
     * Grava [bytes] como JPEG em `Pictures/HashLens` com o nome [displayName].
     *
     * @return URI do MediaStore do arquivo gravado.
     * @throws IOException se a galeria recusar a gravação; nada fica para trás.
     */
    suspend fun save(bytes: ByteArray, displayName: String): Uri = withContext(ioDispatcher) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
                // Invisível para outros apps até terminar de gravar
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, legacyFile(displayName).absolutePath)
            }
        }
        val uri = resolver.insert(collection(), values) ?: throw IOException("a galeria recusou a gravação")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("sem acesso de escrita: $uri")
            output.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        uri
    }

    /**
     * Bytes do arquivo exatamente como estão na galeria.
     *
     * @throws FileNotFoundException se o arquivo foi removido.
     */
    suspend fun read(uri: Uri): ByteArray = withContext(ioDispatcher) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("arquivo indisponível: $uri")
        input.use { it.readBytes() }
    }

    private fun collection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    /** Android 8 e 9: o MediaStore ainda não tem `RELATIVE_PATH`; o caminho vai em `DATA`. */
    private fun legacyFile(displayName: String): File {
        @Suppress("DEPRECATION")
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(pictures, ALBUM).apply { mkdirs() }
        return File(dir, displayName)
    }

    private companion object {
        const val ALBUM = "HashLens"
        const val MIME_JPEG = "image/jpeg"
        val RELATIVE_DIR = "${Environment.DIRECTORY_PICTURES}/$ALBUM"
    }
}
