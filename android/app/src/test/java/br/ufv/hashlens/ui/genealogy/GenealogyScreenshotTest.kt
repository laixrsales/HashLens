package br.ufv.hashlens.ui.genealogy

import br.ufv.hashlens.ui.ScreenshotTest
import br.ufv.hashlens.ui.preview.SampleData
import org.junit.Test

/**
 * T082: Histórico da imagem em todos os estados de ux.md §4.9 (carregando, cadeia simples, com
 * ramificações, limite excedido, nó com assinatura inválida), mais ramificações abertas e sem conexão.
 */
class GenealogyScreenshotTest : ScreenshotTest() {
    private fun genealogy(name: String, state: GenealogyUiState) = screenSnapshot("Historico_$name") {
        GenealogyScreen(state, onAction = {}, zone = SampleData.ZONE)
    }

    @Test
    fun loading() = genealogy("carregando", GenealogySamples.loading)

    @Test
    fun simpleChain() = genealogy("cadeia_simples", GenealogySamples.simpleChain)

    @Test
    fun withBranches() = genealogy("com_ramificacoes", GenealogySamples.withBranches)

    @Test
    fun branchesExpanded() = genealogy("ramificacoes_abertas", GenealogySamples.branchesExpanded)

    @Test
    fun truncated() = genealogy("limite_excedido", GenealogySamples.truncated)

    @Test
    fun invalidSignature() = genealogy("assinatura_invalida", GenealogySamples.invalidSignature)

    @Test
    fun network() = genealogy("sem_conexao", GenealogySamples.network)
}
