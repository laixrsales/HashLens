package br.ufv.hashlens.ui.common

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import java.util.Locale
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T018: exibição do percentual de alteração (hashing-spec §3, research R24, spec Edge Cases).
 *
 * Robolectric porque os textos vêm de `res/values/strings.xml`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PercentFormatTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun percentualUsaVirgulaDecimal() {
        PercentFormat.alteration(resources, 12.5) shouldBe "≈ 12,5% de alteração visual"
    }

    @Test
    fun percentualSempreTemUmaCasaDecimal() {
        PercentFormat.alteration(resources, 12.0) shouldBe "≈ 12,0% de alteração visual"
        PercentFormat.alteration(resources, 1.6) shouldBe "≈ 1,6% de alteração visual"
        PercentFormat.alteration(resources, 100.0) shouldBe "≈ 100,0% de alteração visual"
    }

    @Test
    fun zeroPorCentoNaoMostraNumero() {
        PercentFormat.alteration(resources, 0.0) shouldBe "Sem alteração visual detectável"
    }

    @Test
    fun zeroPorCentoEmVersaoEditadaAvisaQueArquivoDifereDaOriginal() {
        PercentFormat.alteration(resources, 0.0, isEditedVersion = true) shouldBe
            "Sem alteração visual detectável — mas este arquivo não é idêntico à original"
    }

    @Test
    fun versaoEditadaComAlteracaoUsaOTextoComum() {
        PercentFormat.alteration(resources, 12.5, isEditedVersion = true) shouldBe "≈ 12,5% de alteração visual"
    }

    @Test
    fun virgulaNaoDependeDoLocaleDoAparelho() {
        Locale.setDefault(Locale.US)

        PercentFormat.alteration(resources, 12.5) shouldBe "≈ 12,5% de alteração visual"
    }
}
