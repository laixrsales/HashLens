package br.ufv.hashlens.ui.verify

import androidx.compose.ui.graphics.ImageBitmap
import br.ufv.hashlens.domain.model.VerificationResult

/** Estado de Verificar imagem (ux.md §4.7). */
sealed interface VerifyUiState {
    /** Nenhuma imagem escolhida ainda. */
    data object Idle : VerifyUiState

    /** Imagem escolhida, em análise; [thumbnail] é `null` enquanto a miniatura não carrega. */
    data class Analyzing(val step: AnalysisStep, val thumbnail: ImageBitmap? = null) : VerifyUiState

    /** O arquivo escolhido não decodifica (ex.: HEIC; FR-040). */
    data object UnsupportedFile : VerifyUiState
}

/** Etapas da análise: lendo a imagem → consultando registros. */
enum class AnalysisStep {
    Reading,
    Querying
}

sealed interface VerifyAction {
    data object Back : VerifyAction

    /** Abre o Photo Picker, único ponto de entrada da verificação (FR-019). */
    data object ChooseImage : VerifyAction
}

/**
 * Estado do Resultado da verificação (ux.md §4.8).
 *
 * @property thumbnail miniatura da imagem verificada; `null` se não carregou.
 */
data class VerificationResultUiState(val result: VerificationResult, val thumbnail: ImageBitmap? = null)

sealed interface VerificationResultAction {
    data object Back : VerificationResultAction

    /** Volta a Verificar imagem e abre o Photo Picker. */
    data object VerifyAnother : VerificationResultAction

    /** Repete a verificação da mesma imagem (sem conexão). */
    data object Retry : VerificationResultAction

    data class ViewHistory(val recordId: Long) : VerificationResultAction

    /** Link dos detalhes técnicos (explorador da rede). */
    data class OpenUrl(val url: String) : VerificationResultAction
}
