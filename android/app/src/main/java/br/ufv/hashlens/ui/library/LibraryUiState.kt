package br.ufv.hashlens.ui.library

import androidx.compose.ui.graphics.ImageBitmap
import br.ufv.hashlens.ui.components.ImageStatus
import java.time.Instant

/**
 * Estado de Meu acervo (ux.md §4.10).
 *
 * @property groups fotos do [filter], agrupadas pela original; o grupo com a foto mais recente vem
 *   primeiro.
 * @property pendingCount e [failedCount] contam o acervo inteiro, para os filtros.
 * @property selected foto com as opções abertas (folha inferior), por `localId`.
 */
data class LibraryUiState(
    val groups: List<LibraryGroup> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.All,
    val loading: Boolean = true,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val selected: Long? = null
) {
    /** Nenhuma foto no acervo, com qualquer filtro. */
    val isEmpty: Boolean get() = !loading && groups.isEmpty() && filter == LibraryFilter.All

    val selectedItem: LibraryItem? get() = selected?.let { id ->
        groups.flatMap { it.items }.firstOrNull {
            it.localId ==
                id
        }
    }
}

enum class LibraryFilter {
    All,

    /** "Registrando…": aguardando a carteira ou a confirmação da rede. */
    Pending,

    /** "Não registrada – tentar de novo". */
    Failed
}

/**
 * Fotos de uma mesma original, a original primeiro e depois as versões por data.
 *
 * @property key `originalRecordId` da cadeia ou, para uma captura que ainda não chegou à rede, o
 *   `localId` dela.
 * @property originalTakenAt quando a original foi salva neste aparelho; `null` quando a original
 *   é de outra pessoa (versões importadas para edição).
 */
data class LibraryGroup(val key: String, val originalTakenAt: Instant?, val items: List<LibraryItem>)

/**
 * Uma foto do acervo.
 *
 * @property recordId registro na rede, quando confirmada.
 * @property thumbnail `null` enquanto carrega ou quando o arquivo não está mais na galeria.
 * @property retrying o "Tentar de novo" está em andamento (a carteira pode estar aberta).
 */
data class LibraryItem(
    val localId: Long,
    val status: ImageStatus,
    val savedAt: Instant,
    val fileAvailable: Boolean,
    val recordId: Long? = null,
    val thumbnail: ImageBitmap? = null,
    val retrying: Boolean = false
) {
    val canRetry: Boolean get() = status == ImageStatus.Failed && !retrying

    /** Só fotos registradas abrem o histórico. */
    val canOpenHistory: Boolean get() = recordId != null

    /** Só fotos registradas e ainda na galeria podem ser editadas (FR-015). */
    val canEdit: Boolean get() = recordId != null && fileAvailable
}

sealed interface LibraryAction {
    data object Back : LibraryAction

    /** Estado vazio: "Registrar foto". */
    data object RegisterPhoto : LibraryAction

    data class SetFilter(val filter: LibraryFilter) : LibraryAction

    /** "Tentar de novo" de uma foto que falhou. */
    data class Retry(val localId: Long) : LibraryAction

    /** Toque numa foto: abre as opções dela. */
    data class Select(val localId: Long) : LibraryAction

    data object DismissSelection : LibraryAction

    data class OpenHistory(val recordId: Long) : LibraryAction

    data class Edit(val localId: Long) : LibraryAction
}
