package br.ufv.hashlens.ui.editor

import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.domain.model.EditOperationCodec

// Regras puras do Editor (EditorViewModel): elegibilidade, valores iniciais e limites dos ajustes

/** Por que uma imagem do acervo não abre no Editor; `null` se abre (T074). */
internal fun ineligibility(image: LocalImageEntity): IneligibleReason? = when {
    image.status == RegistrationStatus.CONFIRMED -> null
    else -> image.status.toIneligible()
}

internal fun RegistrationStatus.toIneligible(): IneligibleReason =
    if (this == RegistrationStatus.FAILED) IneligibleReason.RegistrationFailed else IneligibleReason.Pending

/** Valor inicial do ajuste de cada ferramenta com parâmetro: sem efeito até mexer, salvo desfoque e bordas. */
internal fun EditorTool.initialDraft(): EditOperation = when (this) {
    EditorTool.Brightness -> EditOperation.Brightness(0)
    EditorTool.Contrast -> EditOperation.Contrast(1f)
    EditorTool.Blur -> EditOperation.Blur(INITIAL_BLUR)
    EditorTool.Edges -> edgesOf(INITIAL_EDGES)
    EditorTool.Crop -> EditOperation.Crop(0f, 0f, 1f, 1f)
    else -> instantOperation()
}

/** Operação das ferramentas aplicadas com um toque; girar é um quarto de volta por toque. */
internal fun EditorTool.instantOperation(): EditOperation = when (this) {
    EditorTool.Grayscale -> EditOperation.Grayscale
    EditorTool.Sepia -> EditOperation.Sepia
    EditorTool.Sharpen -> EditOperation.Sharpen
    EditorTool.Rotate -> EditOperation.Rotate90(1)
    else -> throw IllegalArgumentException("$this tem parâmetro")
}

internal fun EditOperation.isIdentity(): Boolean = when (this) {
    is EditOperation.Brightness -> delta == 0
    is EditOperation.Contrast -> factor == 1f
    is EditOperation.Crop -> x == 0f && y == 0f && w == 1f && h == 1f
    else -> false
}

/** O texto on-chain das operações tem no máximo 256 bytes (hashing-spec §4). */
internal fun fitsInRecord(operations: List<EditOperation>): Boolean = try {
    EditOperationCodec.encode(operations)
    true
} catch (_: IllegalArgumentException) {
    false
}

private const val INITIAL_BLUR = 5
private const val INITIAL_EDGES = 50
