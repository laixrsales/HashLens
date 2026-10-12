package br.ufv.hashlens.ui.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Meu acervo no aparelho (US6, ux.md §4.10): estado vazio como convite, grupos pela original, selos
 * do vocabulário de status, "Tentar de novo" nas que falharam, filtros e a folha de opções de cada
 * foto. Os textos são os de `strings.xml`, em pt-BR.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<LibraryAction>()

    private fun show(state: LibraryUiState) = compose.setContent {
        HashLensTheme { LibraryScreen(state, onAction = { actions += it }, zone = SampleData.ZONE) }
    }

    @Test
    fun vazioConvidaARegistrarFoto() {
        show(LibrarySamples.empty)

        compose.onNodeWithText("Nenhuma foto registrada ainda").assertIsDisplayed()
        compose.onNodeWithText("Registrar foto").performClick()

        actions shouldContainExactly listOf(LibraryAction.RegisterPhoto)
    }

    @Test
    fun agrupaPelaOriginalComOsSelosDeStatus() {
        show(LibrarySamples.withItems)

        compose.onNodeWithText("Original de 2 out. 2026, 09:05").assertIsDisplayed()
        compose.onAllNodesWithText("Registrada – original").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Registrada – versão alterada").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Não registrada – tentar de novo").onFirst().assertIsDisplayed()
    }

    @Test
    fun tentarDeNovoReenviaAFotoQueFalhou() {
        show(LibrarySamples.failed)

        // A primeira já está reenviando; só a outra oferece o botão
        compose.onAllNodesWithText("Abrindo a carteira…").assertCountEquals(1)
        compose.onNodeWithText("Tentar de novo").performScrollTo().performClick()

        actions shouldContainExactly listOf(LibraryAction.Retry(3))
    }

    @Test
    fun filtrosMostramAContagemETrocamOFiltro() {
        show(LibrarySamples.withItems)

        compose.onNodeWithText("Com falha (2)").performClick()

        actions shouldContainExactly listOf(LibraryAction.SetFilter(LibraryFilter.Failed))
    }

    @Test
    fun tocarNaFotoAbreAsOpcoes() {
        show(LibrarySamples.withItems)

        compose.onAllNodesWithText("4 out. 2026, 14:32").onFirst().performClick()

        actions shouldContainExactly listOf(LibraryAction.Select(2))
    }

    @Test
    fun opcoesDaFotoRegistradaLevamAoHistoricoEAEdicao() {
        show(LibrarySamples.withItems.copy(selected = 2))

        compose.onNodeWithText("Editar foto").performClick()
        compose.onNodeWithText("Ver histórico da imagem").performClick()

        actions shouldContainExactly listOf(LibraryAction.Edit(2), LibraryAction.OpenHistory(11))
    }

    @Test
    fun fotoForaDaGaleriaNaoOfereceEdicao() {
        show(LibrarySamples.fileRemoved.copy(selected = 1))

        compose.onAllNodesWithText("Não está mais na galeria").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Editar foto").assertDoesNotExist()
        compose.onNodeWithText("Ver histórico da imagem").assertIsDisplayed()
    }
}
