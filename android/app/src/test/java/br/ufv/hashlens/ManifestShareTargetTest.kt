package br.ufv.hashlens

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * T061: o app não recebe imagens de outros aplicativos (FR-019, research R16). A verificação começa
 * só pelo Photo Picker; nenhuma activity do manifesto mesclado (app e bibliotecas) pode resolver
 * `ACTION_SEND` ou `ACTION_VIEW` de imagens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ManifestShareTargetTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun activitiesFor(intent: Intent): List<String> = context.packageManager
        .queryIntentActivities(intent.setPackage(context.packageName), PackageManager.MATCH_ALL)
        .map { it.activityInfo.name }

    @Test
    fun `nenhuma activity recebe imagens compartilhadas por ACTION_SEND`() {
        IMAGE_TYPES.forEach { type ->
            val share = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, IMAGE_URI)

            activitiesFor(share).shouldBeEmpty()
        }
    }

    @Test
    fun `nenhuma activity recebe várias imagens por ACTION_SEND_MULTIPLE`() {
        IMAGE_TYPES.forEach { type ->
            activitiesFor(Intent(Intent.ACTION_SEND_MULTIPLE).setType(type)).shouldBeEmpty()
        }
    }

    @Test
    fun `nenhuma activity abre imagens por ACTION_VIEW`() {
        IMAGE_TYPES.forEach { type ->
            listOf(IMAGE_URI, FILE_URI).forEach { uri ->
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addCategory(Intent.CATEGORY_DEFAULT)

                activitiesFor(view).shouldBeEmpty()
            }
        }
    }

    @Test
    fun `a consulta enxerga os filtros do manifesto (controle)`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val walletReturn = Intent(Intent.ACTION_VIEW, Uri.parse("hashlens-wc://request"))
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory(Intent.CATEGORY_BROWSABLE)

        activitiesFor(launcher) shouldContain MainActivity::class.java.name
        activitiesFor(walletReturn) shouldContain MainActivity::class.java.name
    }

    private companion object {
        val IMAGE_TYPES = listOf("image/*", "image/jpeg", "image/png", "image/webp", "image/heic")
        val IMAGE_URI: Uri = Uri.parse("content://media/external/images/media/42")
        val FILE_URI: Uri = Uri.parse("file:///sdcard/DCIM/foto.jpg")
    }
}
