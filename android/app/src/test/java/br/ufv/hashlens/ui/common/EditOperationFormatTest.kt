package br.ufv.hashlens.ui.common

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import br.ufv.hashlens.domain.model.EditOperation
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Operações em linguagem leiga (ux.md §4.9), no aviso de permanência do Editor e no histórico.
 *
 * Robolectric porque os textos vêm de `res/values/strings.xml`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditOperationFormatTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun listaNaOrdemComSoAPrimeiraMaiuscula() {
        val operations = listOf(EditOperation.Grayscale, EditOperation.Brightness(30))

        EditOperationFormat.list(resources, operations) shouldBe "Preto e branco, brilho +30"
    }

    @Test
    fun cadaOperacaoComOValorEmPtBr() {
        fun one(operation: EditOperation) = EditOperationFormat.one(resources, operation)

        one(EditOperation.Brightness(-15)) shouldBe "Brilho −15"
        one(EditOperation.Brightness(0)) shouldBe "Brilho 0"
        one(EditOperation.Contrast(1.25f)) shouldBe "Contraste 1,25"
        one(EditOperation.Contrast(0.5f)) shouldBe "Contraste 0,50"
        one(EditOperation.Sepia) shouldBe "Sépia"
        one(EditOperation.Blur(7)) shouldBe "Desfoque 7"
        one(EditOperation.Sharpen) shouldBe "Nitidez"
        one(EditOperation.Edges(50, 150)) shouldBe "Bordas"
        one(EditOperation.Crop(0.1f, 0.1f, 0.8f, 0.8f)) shouldBe "Recorte"
        one(EditOperation.Rotate90(1)) shouldBe "Rotação de 90°"
        one(EditOperation.Rotate90(3)) shouldBe "Rotação de 270°"
    }
}
