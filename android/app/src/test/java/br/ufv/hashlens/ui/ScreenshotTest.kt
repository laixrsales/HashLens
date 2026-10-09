package br.ufv.hashlens.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import br.ufv.hashlens.ui.theme.HashLensTheme
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base dos screenshot tests (Roborazzi + Robolectric, gráficos nativos).
 *
 * Cada [snapshot] gera três PNGs em `app/build/outputs/roborazzi/`: tema claro, escuro e fonte
 * em 200% (hashlens-ui: claro e escuro, nada quebra com fonte em 200%). Gravar:
 * `./gradlew :app:recordRoborazziDebug`; comparar com os gravados: `verifyRoborazziDebug`. No
 * `testDebugUnitTest` comum a composição roda (e falha se quebrar), mas nenhuma imagem é gravada.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Largura de telefone médio (411 dp, xxhdpi) e janela alta, para nenhum conjunto de estados ser
// cortado (o PNG tem só a altura do conteúdo); Application simples, sem Hilt nem OpenCV nativo
@Config(application = Application::class, qualifiers = "w411dp-h2400dp-xxhdpi")
abstract class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    enum class Variant(val suffix: String, val dark: Boolean, val fontScale: Float) {
        Light("claro", dark = false, fontScale = 1f),
        Dark("escuro", dark = true, fontScale = 1f),
        LargeFont("fonte200", dark = false, fontScale = 2f)
    }

    /** Renderiza [content] em cada [Variant] e grava `<name>_<variante>.png`. */
    protected fun snapshot(name: String, content: @Composable () -> Unit) {
        // Relógio manual: animações infinitas (indicador de progresso) não deixariam a tela ociosa;
        // avançar o relógio leva as animações finitas (régua, expandir) ao estado final
        composeRule.mainClock.autoAdvance = false
        // Começa sem variante: toda captura, inclusive a primeira, vem depois de uma recomposição.
        // Capturar o estado inicial sem nenhuma mudança saía em branco quando o conteúdo não tinha
        // animação (TechnicalDetails recolhido): a janela ainda não tinha desenhado o quadro.
        val variant = mutableStateOf<Variant?>(null)
        composeRule.setContent {
            val current = variant.value ?: return@setContent
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, current.fontScale)) {
                HashLensTheme(darkTheme = current.dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(
                            Modifier
                                .testTag(TAG)
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(HashLensTheme.spacing.lg)
                        ) { content() }
                    }
                }
            }
        }
        Variant.entries.forEach {
            composeRule.runOnIdle { variant.value = it }
            // Processa a mudança de estado antes de avançar o relógio, senão a captura sai com a variante anterior
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(SETTLE_MS)
            composeRule.waitForIdle()
            val path = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/${name}_${it.suffix}.png"
            composeRule.onNodeWithTag(TAG).captureRoboImage(path)
        }
    }

    private companion object {
        const val TAG = "screenshot"
        const val SETTLE_MS = 2_000L
    }
}
