package br.ufv.hashlens.ui.genealogy

import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.domain.model.GenealogyNode
import br.ufv.hashlens.ui.preview.SampleData.Records
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Linha do tempo do Histórico: o caminho na árvore e o que fica recolhido em cada passo. */
class GenealogyTimelineTest {
    private fun node(id: Int, vararg children: GenealogyNode) = GenealogyNode(
        record = Records.original.copy(id = id.toLong()),
        percentFromParent = if (id == 1) null else 0.0,
        percentFromOriginal = 0.0,
        signatureValid = true,
        children = children.toList()
    )

    // 1 → (2 → (4, 5 → 6), 3)
    private val tree = node(1, node(2, node(4), node(5, node(6))), node(3))

    @Test
    fun `segue o caminho da original até a imagem consultada`() {
        val timeline = Genealogy(tree, pathToQueried = listOf(1, 2, 4), truncated = false).timeline()

        timeline.map { it.node.record.id } shouldContainExactly listOf(1L, 2L, 4L)
        timeline.map { it.isQueried } shouldContainExactly listOf(false, false, true)
    }

    @Test
    fun `em cada passo, as versões fora do caminho ficam recolhidas com as derivadas contadas`() {
        val timeline = Genealogy(tree, pathToQueried = listOf(1, 2, 4), truncated = false).timeline()

        timeline[0].branches.map { it.record.id } shouldContainExactly listOf(3L)
        timeline[0].hiddenCount shouldBe 1
        // A versão 5 tem uma derivada (6): duas versões recolhidas
        timeline[1].branches.map { it.record.id } shouldContainExactly listOf(5L)
        timeline[1].hiddenCount shouldBe 2
        timeline[2].hiddenCount shouldBe 0
    }

    @Test
    fun `na imagem consultada, todas as versões derivadas ficam recolhidas`() {
        val timeline = Genealogy(tree, pathToQueried = listOf(1, 2), truncated = false).timeline()

        timeline.last().branches.map { it.record.id } shouldContainExactly listOf(4L, 5L)
        timeline.last().hiddenCount shouldBe 3
    }
}
