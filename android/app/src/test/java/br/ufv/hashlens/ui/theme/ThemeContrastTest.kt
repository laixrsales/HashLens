package br.ufv.hashlens.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.pow
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * T034: contraste WCAG 2.1 AA dos tokens, nos temas claro e escuro (direção visual §2.3).
 * Texto ≥ 4,5:1; contornos e ícones ≥ 3:1. Uma cor nova que quebre o contraste falha aqui.
 */
class ThemeContrastTest {
    class ThemeTokens(private val name: String, val scheme: ColorScheme, val status: StatusColors) {
        override fun toString() = name
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `texto e componentes base passam no AA`(theme: ThemeTokens) {
        val scheme = theme.scheme
        assertSoftly {
            scheme.onBackground.shouldContrastWith(scheme.background, TEXT, "texto sobre fundo")
            listOf(
                scheme.surface,
                scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest
            ).forEachIndexed { i, surface ->
                scheme.onSurface.shouldContrastWith(surface, TEXT, "texto sobre superfície $i")
                scheme.onSurfaceVariant.shouldContrastWith(surface, TEXT, "texto secundário sobre superfície $i")
            }
            scheme.outline.shouldContrastWith(scheme.background, NON_TEXT, "contorno sobre fundo")
            scheme.onPrimary.shouldContrastWith(scheme.primary, TEXT, "botão primário")
            scheme.onPrimaryContainer.shouldContrastWith(scheme.primaryContainer, TEXT, "container primário")
            scheme.error.shouldContrastWith(scheme.background, TEXT, "erro sobre fundo")
            scheme.onError.shouldContrastWith(scheme.error, TEXT, "texto sobre erro")
            scheme.onErrorContainer.shouldContrastWith(scheme.errorContainer, TEXT, "container de erro")
            scheme.inverseOnSurface.shouldContrastWith(scheme.inverseSurface, TEXT, "snackbar")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `cores de status passam no AA`(theme: ThemeTokens) {
        val scheme = theme.scheme
        assertSoftly {
            theme.status.all.forEachIndexed { i, color ->
                color.content.shouldContrastWith(scheme.background, TEXT, "status $i sobre fundo")
                color.content.shouldContrastWith(color.container, TEXT, "status $i sobre o próprio fundo")
                scheme.onSurface.shouldContrastWith(color.container, TEXT, "texto sobre o fundo do status $i")
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `cores de status distintas, exceto adulterado e falha`(theme: ThemeTokens) {
        val status = theme.status
        // Adulterado e falha compartilham o Vermelho óxido (nunca na mesma tela; direção visual §2.2)
        val distinct = status.all.map { it.content }.toSet()
        distinct.size shouldBe status.all.size - 1
        status.tampered shouldBe status.failed
        status.failed.content shouldBe theme.scheme.error
    }

    @Test
    fun `contraste confere com valores conhecidos da WCAG`() {
        contrast(Color.Black, Color.White) shouldBe (21.0 plusOrMinus 1e-9)
        contrast(Color(0xFF767676), Color.White).shouldBeGreaterThanOrEqual(TEXT)
    }

    private fun Color.shouldContrastWith(background: Color, minimum: Double, what: String) {
        withClue("$what: ${hex()} sobre ${background.hex()}") {
            contrast(this, background) shouldBeGreaterThanOrEqual minimum
        }
    }

    private fun Color.hex(): String = "#%06X".format(toArgbRgb())

    private fun Color.toArgbRgb(): Int =
        ((red * MAX).toInt() shl 16) or ((green * MAX).toInt() shl 8) or (blue * MAX).toInt()

    companion object {
        private const val TEXT = 4.5
        private const val NON_TEXT = 3.0
        private const val MAX = 255

        @JvmStatic
        fun themes() = listOf(
            ThemeTokens("claro", LightColorScheme, LightStatusColors),
            ThemeTokens("escuro", DarkColorScheme, DarkStatusColors)
        )

        /** Razão de contraste WCAG 2.1 entre duas cores opacas. */
        fun contrast(a: Color, b: Color): Double {
            val (light, dark) = listOf(a.relativeLuminance(), b.relativeLuminance()).sortedDescending()
            return (light + 0.05) / (dark + 0.05)
        }

        private fun Color.relativeLuminance(): Double {
            fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
        }
    }
}
