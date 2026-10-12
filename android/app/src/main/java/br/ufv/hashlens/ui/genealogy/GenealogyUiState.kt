package br.ufv.hashlens.ui.genealogy

import androidx.compose.ui.graphics.ImageBitmap
import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.domain.model.GenealogyNode
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason

/** Estado do Histórico da imagem (ux.md §4.9). */
sealed interface GenealogyUiState {
    data object Loading : GenealogyUiState

    /** Não foi possível consultar o contrato. */
    data class Error(val reason: Reason) : GenealogyUiState

    /**
     * @property thumbnails miniaturas das versões que estão neste aparelho, por id do registro.
     * @property expanded nós da linha do tempo com as outras versões abertas.
     */
    data class Loaded(
        val genealogy: Genealogy,
        val thumbnails: Map<Long, ImageBitmap> = emptyMap(),
        val expanded: Set<Long> = emptySet()
    ) : GenealogyUiState
}

sealed interface GenealogyAction {
    data object Back : GenealogyAction

    data object Retry : GenealogyAction

    /** Abre ou recolhe as versões fora do caminho que saem do nó [recordId]. */
    data class ToggleBranches(val recordId: Long) : GenealogyAction

    /** Abre o histórico de outra versão da árvore. */
    data class OpenRecord(val recordId: Long) : GenealogyAction

    /** Abre a transação do registro no explorador de blocos. */
    data class OpenTransaction(val url: String) : GenealogyAction
}

/**
 * Um passo da linha do tempo: um nó do caminho da original até a imagem consultada.
 *
 * @property branches versões que saem deste nó e não estão no caminho, recolhidas por padrão.
 * @property hiddenCount quantas versões existem nessas ramificações, contando as derivadas delas.
 */
data class TimelineEntry(
    val node: GenealogyNode,
    val isQueried: Boolean,
    val branches: List<GenealogyNode>,
    val hiddenCount: Int
)

/** Linha do tempo da original até a imagem consultada, seguindo [Genealogy.pathToQueried] na árvore. */
fun Genealogy.timeline(): List<TimelineEntry> {
    val entries = mutableListOf<TimelineEntry>()
    var node: GenealogyNode? = root
    pathToQueried.forEachIndexed { index, id ->
        val current = node?.takeIf { it.record.id == id } ?: return entries
        val next = pathToQueried.getOrNull(index + 1)
        val branches = current.children.filterNot { it.record.id == next }
        entries += TimelineEntry(
            node = current,
            isQueried = index == pathToQueried.lastIndex,
            branches = branches,
            hiddenCount = branches.sumOf { it.size() }
        )
        node = current.children.firstOrNull { it.record.id == next }
    }
    return entries
}

/** Quantos nós há nesta subárvore, contando o próprio. */
fun GenealogyNode.size(): Int = 1 + children.sumOf { it.size() }
