package br.ufv.hashlens.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.ufv.hashlens.ui.theme.HashLensTheme
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T106: "Como funciona" no aparelho (FR-037, ux.md §4.1): as três ideias, o aviso de rede de testes e
 * os formatos aceitos e não aceitos (FR-040), em pt-BR, com "Começar" e "Pular" fixos embaixo.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<String>()

    private fun show() = compose.setContent {
        HashLensTheme { OnboardingScreen(onStart = { actions += "começar" }, onSkip = { actions += "pular" }) }
    }

    private fun assertText(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun explicaRegistrarVerificarEEditar() {
        show()

        assertText("Como funciona")
        assertText("Registrar")
        assertText("Verificar")
        assertText("Editar")
    }

    @Test
    fun avisaDaRedeDeTestesEDosFormatos() {
        show()

        assertText("O HashLens usa a Sepolia, uma rede de testes: registrar não custa dinheiro de verdade.")
        assertText("Aceita JPEG, PNG e WebP. Fotos em HEIC ou HEIF não são aceitas.")
    }

    @Test
    fun comecarEPular() {
        show()

        compose.onNodeWithText("Começar").performClick()
        compose.onNodeWithText("Pular").performClick()

        actions shouldContainExactly listOf("começar", "pular")
    }
}
