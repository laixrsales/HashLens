package br.ufv.hashlens.ui.editor

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Deblur
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.EditOperation

/** Estado do Editor (ux.md §4.6). */
sealed interface EditorUiState {
    /** Lendo e decodificando a imagem a editar. */
    data object Loading : EditorUiState

    /** A imagem não pode ser editada; a tela explica por quê. */
    data class NotEligible(val reason: IneligibleReason) : EditorUiState

    /**
     * @property preview imagem com as [operations] aplicadas, em resolução reduzida.
     * @property original imagem sem alterações, mostrada enquanto a pessoa toca e segura (antes/depois).
     * @property draft ajuste em curso da ferramenta [activeTool], já na prévia, ainda não aplicado.
     * @property originalAuthor carteira da original quando a imagem é de outra pessoa (US4); `null` se é da própria.
     * @property processing aplicando em resolução cheia e gravando; os controles ficam desabilitados.
     * @property confirmingSave aviso de permanência aberto, antes de abrir a carteira.
     */
    data class Editing(
        val preview: ImageBitmap,
        val original: ImageBitmap,
        val operations: List<EditOperation> = emptyList(),
        val activeTool: EditorTool? = null,
        val draft: EditOperation? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val showingOriginal: Boolean = false,
        val originalAuthor: String? = null,
        val processing: Boolean = false,
        val confirmingSave: Boolean = false
    ) : EditorUiState {
        /** "Salvar e registrar" só com alguma alteração aplicada. */
        val canSave: Boolean get() = operations.isNotEmpty() && draft == null && !processing
    }
}

/** Por que a imagem não abre no editor (US3 cenários 3 e 5, FR-015). */
enum class IneligibleReason {
    /** O registro ainda não foi confirmado pela rede. */
    Pending,

    /** O arquivo não é cópia exata de nenhum registro (inclusive correspondência só visual, R18). */
    NotRegistered,

    /** O arquivo saiu da galeria ou mudou desde o registro. */
    FileUnavailable
}

/**
 * Ferramentas da faixa de filtros, uma por operação de hashing-spec §4. As que têm parâmetro
 * abrem o controle de ajuste; as demais são aplicadas com um toque.
 */
enum class EditorTool(val icon: ImageVector, @StringRes val label: Int, val hasParameter: Boolean) {
    Brightness(Icons.Outlined.WbSunny, R.string.tool_brightness, hasParameter = true),
    Contrast(Icons.Outlined.Contrast, R.string.tool_contrast, hasParameter = true),
    Grayscale(Icons.Outlined.FilterBAndW, R.string.tool_grayscale, hasParameter = false),
    Sepia(Icons.Outlined.Gradient, R.string.tool_sepia, hasParameter = false),
    Blur(Icons.Outlined.BlurOn, R.string.tool_blur, hasParameter = true),
    Sharpen(Icons.Outlined.Deblur, R.string.tool_sharpen, hasParameter = false),
    Edges(Icons.Outlined.Texture, R.string.tool_edges, hasParameter = true),
    Crop(Icons.Outlined.Crop, R.string.tool_crop, hasParameter = true),
    Rotate(Icons.AutoMirrored.Outlined.RotateRight, R.string.tool_rotate, hasParameter = false)
}

sealed interface EditorAction {
    data object Close : EditorAction

    /** Toque numa ferramenta: aplica as sem parâmetro e abre o ajuste das demais. */
    data class SelectTool(val tool: EditorTool) : EditorAction

    /** Novo valor do ajuste em curso (controle deslizante ou moldura do recorte). */
    data class UpdateDraft(val operation: EditOperation) : EditorAction

    data object ApplyDraft : EditorAction

    data object CancelDraft : EditorAction

    data object Undo : EditorAction

    data object Redo : EditorAction

    /** Toque e segure na foto: mostra a original até soltar. */
    data class Compare(val showingOriginal: Boolean) : EditorAction

    /** "Salvar e registrar": abre o aviso de permanência. */
    data object Save : EditorAction

    /** "Salvar e registrar" no aviso: aplica em resolução cheia e abre a carteira. */
    data object ConfirmSave : EditorAction

    data object DismissSave : EditorAction
}

/** Bordas com um controle só: a sensibilidade é o limiar inferior, e o superior é o triplo (Canny 1:3). */
internal fun edgesOf(sensitivity: Int): EditOperation.Edges =
    EditOperation.Edges(low = sensitivity, high = (sensitivity * EDGES_RATIO).coerceAtMost(MAX_LEVEL))

private const val EDGES_RATIO = 3
private const val MAX_LEVEL = 255
