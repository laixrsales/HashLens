package br.ufv.hashlens.ui.verify

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.ufv.hashlens.domain.model.VerificationResult
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.ui.preview.SampleData.Percent
import br.ufv.hashlens.ui.preview.SampleData.Records
import br.ufv.hashlens.ui.theme.HashLensTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T060: título e mensagem obrigatória de cada resultado (verification-result §3) e o rodapé obrigatório
 * dos resultados positivos. Os textos são os do contrato, literalmente, em pt-BR.
 *
 * A quantidade da correspondência visual usa plural ("2 registros") em vez do "registro(s)" do
 * contrato (hashlens-ui: plurais em `<plurals>`).
 *
 * Os resultados aparecem em [VerificationResultScreen]; o arquivo não suportado aparece na própria
 * [VerifyScreen], que nomeia os formatos aceitos (ux.md §4.7 e §4.8).
 */
@RunWith(AndroidJUnit4::class)
class VerifyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showResult(result: VerificationResult) = compose.setContent {
        HashLensTheme { VerificationResultScreen(VerificationResultUiState(result), onAction = {}) }
    }

    private fun showVerify(state: VerifyUiState) = compose.setContent {
        HashLensTheme { VerifyScreen(state = state, onAction = {}) }
    }

    /** Título visível no topo (hierarquia 1 de ux.md §4.8). */
    private fun assertTitle(title: String) {
        compose.onAllNodesWithText(title).onFirst().assertIsDisplayed()
    }

    private fun assertText(text: String, substring: Boolean = false) {
        compose.onAllNodesWithText(text, substring = substring).onFirst().assertExists()
    }

    /** Rodapé obrigatório de todo resultado positivo, ao fim da rolagem (direção visual §7.2). */
    private fun assertFooter() {
        compose.onNodeWithText(FOOTER).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun registradaOriginal() {
        showResult(VerificationResult.RegisteredOriginal(Records.original))

        assertTitle("Registrada – original")
        assertText("Este arquivo é idêntico a uma captura registrada.")
        assertFooter()
    }

    @Test
    fun registradaVersaoAlteradaComPercentualEmPtBr() {
        showResult(VerificationResult.RegisteredEdited(Records.edited, Records.original, Percent.EDITED))

        assertTitle("Registrada – versão alterada")
        assertText("Versão editada registrada. ≈ 12,5% de alteração visual em relação à original.")
        assertFooter()
    }

    @Test
    fun versaoAlteradaComZeroPorCentoAvisaQueNaoEIdentica() {
        showResult(VerificationResult.RegisteredEdited(Records.editedZero, Records.original, Percent.ZERO))

        assertTitle("Registrada – versão alterada")
        // Spec, Edge Cases: 0% não pode soar como "idêntica à original"
        assertText("Sem alteração visual detectável — mas este arquivo não é idêntico à original", substring = true)
        assertFooter()
    }

    @Test
    fun correspondenciaVisualInformaQuantosRegistros() {
        val candidates = Records.visualMatchCandidates.map { record ->
            VerificationResult.VisualMatch.Candidate(record, Records.original, Percent.ZERO)
        }

        showResult(VerificationResult.VisualMatch(candidates))

        assertTitle("Correspondência visual")
        assertText(
            "O arquivo não é idêntico a nenhum registro, mas é visualmente igual a 2 registros. " +
                "Pode ser uma cópia recomprimida."
        )
        assertFooter()
    }

    @Test
    fun registroAdulterado() {
        showResult(VerificationResult.Tampered(Records.tampered))

        assertTitle("Registro adulterado")
        assertText("A assinatura do aparelho não confere com os dados registrados.")
    }

    @Test
    fun naoRegistradaNaoAfirmaQueAImagemEFalsa() {
        showResult(VerificationResult.NotRegistered)

        assertTitle("Não registrada")
        assertText("Nenhum registro encontrado. Isso não significa que a imagem seja falsa.")
    }

    @Test
    fun semConexaoNaoEConfundidaComNaoRegistrada() {
        showResult(VerificationResult.Error(Reason.NETWORK))

        assertTitle("Não foi possível verificar")
        assertText("Sem conexão com a rede. Tente novamente.")
        compose.onAllNodesWithText("Não registrada").assertCountEquals(0)
    }

    @Test
    fun arquivoNaoSuportadoNomeiaOsFormatosAceitos() {
        showVerify(VerifyUiState.UnsupportedFile)

        assertTitle("Formato não suportado")
        assertText(
            "Não foi possível ler este arquivo. Use uma imagem JPEG, PNG ou WebP; fotos em HEIC não são aceitas."
        )
    }

    private companion object {
        const val FOOTER =
            "O registro comprova quando e por quem o arquivo foi registrado, não que a cena retratada seja real."
    }
}
