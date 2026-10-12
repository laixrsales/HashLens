package br.ufv.hashlens.ui.genealogy

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.preview.SampleData.Records
import br.ufv.hashlens.ui.theme.HashLensTheme
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Histórico da imagem no aparelho (US5, ux.md §4.9; quickstart, roteiro US5): a linha do tempo da
 * original até a imagem consultada, em ordem, com as outras versões recolhidas, os avisos e o link
 * da transação de cada nó. Os textos são os de `strings.xml`, em pt-BR.
 */
@RunWith(AndroidJUnit4::class)
class GenealogyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<GenealogyAction>()

    private fun show(state: GenealogyUiState) = compose.setContent {
        HashLensTheme { GenealogyScreen(state, onAction = { actions += it }, zone = SampleData.ZONE) }
    }

    private fun assertText(text: String) {
        compose.onAllNodesWithText(text).onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cadeiaDeTresGeracoesMostraOsTresNosEmOrdem() {
        show(GenealogySamples.withBranches)

        // Original (2 out.) → versão (4 out.) → versão da versão (6 out.), a consultada destacada
        val dates = listOf("2 out. 2026, 09:05", "4 out. 2026, 14:32", "6 out. 2026, 06:40")
        val tops = dates.map { date ->
            compose.onNodeWithText(date).performScrollTo().assertIsDisplayed().fetchSemanticsNode().positionInRoot.y
        }
        tops shouldContainExactly tops.sorted()
        compose.onAllNodesWithText("Imagem consultada").assertCountEquals(1)
        assertText("Registrada por 0x12ab…9f3c")
    }

    @Test
    fun outrasVersoesFicamRecolhidasEAbremAoTocar() {
        show(GenealogySamples.withBranches)

        compose.onNodeWithText("+2 outras versões").performScrollTo().performClick()

        actions shouldContainExactly listOf(GenealogyAction.ToggleBranches(Records.original.id))
    }

    @Test
    fun versoesAbertasLevamAoHistoricoDeCadaUma() {
        show(GenealogySamples.branchesExpanded)

        compose.onNodeWithText("7 out. 2026, 05:20").performScrollTo().performClick()

        actions shouldContainExactly listOf(GenealogyAction.OpenRecord(Records.visualMatchCandidates.last().id))
    }

    @Test
    fun cadaNoComTransacaoConhecidaTemOLinkParaElaNaRede() {
        show(GenealogySamples.simpleChain)

        // A original da amostra não tem transação conhecida: só a versão consultada tem o link
        compose.onAllNodesWithText("Ver registro na rede").assertCountEquals(1)
        compose.onNodeWithText("Ver registro na rede").performScrollTo().performClick()

        actions shouldContainExactly listOf(GenealogyAction.OpenTransaction(Records.edited.txUrl!!))
    }

    @Test
    fun limiteExcedidoAvisaQuantoAparece() {
        show(GenealogySamples.truncated)

        assertText(
            "Esta imagem tem mais versões do que cabem aqui. Mostrando até ${Genealogy.MAX_LEVELS} gerações e " +
                "${Genealogy.MAX_NODES} versões."
        )
    }

    @Test
    fun noComAssinaturaInvalidaFicaNaLinhaDoTempoSinalizado() {
        show(GenealogySamples.invalidSignature)

        assertText("Registro adulterado")
        assertText("A assinatura do aparelho não confere com os dados registrados.")
        compose.onAllNodesWithText("Imagem consultada").assertCountEquals(1)
    }

    @Test
    fun semConexaoOfereceTentarDeNovo() {
        show(GenealogySamples.network)

        assertText("Não foi possível carregar o histórico")
        compose.onNodeWithText("Tentar de novo").performClick()

        actions shouldContainExactly listOf(GenealogyAction.Retry)
    }
}
