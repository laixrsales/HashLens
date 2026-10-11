package br.ufv.hashlens.ui.common

import android.content.res.Resources
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.EditOperation
import java.text.NumberFormat
import java.util.Locale

/**
 * Operações de edição em linguagem leiga (ux.md §4.9: "Preto e branco, brilho +30"), em pt-BR. O
 * texto técnico on-chain (hashing-spec §4) fica nos detalhes técnicos.
 */
object EditOperationFormat {
    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

    /** Lista na ordem aplicada; a primeira com maiúscula, as demais em minúsculas. */
    fun list(resources: Resources, operations: List<EditOperation>): String = operations
        .mapIndexed { index, operation ->
            one(resources, operation).let { if (index == 0) it else it.replaceFirstChar(Char::lowercaseChar) }
        }
        .joinToString(", ")

    fun one(resources: Resources, operation: EditOperation): String = when (operation) {
        is EditOperation.Brightness -> resources.getString(R.string.operation_brightness, signed(operation.delta))
        is EditOperation.Contrast -> resources.getString(R.string.operation_contrast, factor(operation.factor))
        EditOperation.Grayscale -> resources.getString(R.string.tool_grayscale)
        EditOperation.Sepia -> resources.getString(R.string.tool_sepia)
        is EditOperation.Blur -> resources.getString(R.string.operation_blur, operation.kernel)
        EditOperation.Sharpen -> resources.getString(R.string.tool_sharpen)
        is EditOperation.Edges -> resources.getString(R.string.tool_edges)
        is EditOperation.Crop -> resources.getString(R.string.operation_crop)
        is EditOperation.Rotate90 -> resources.getString(R.string.operation_rotate, operation.turns * QUARTER_TURN)
    }

    /** `30` → `"+30"`, `-15` → `"−15"` (sinal de menos tipográfico), `0` → `"0"`. */
    fun signed(value: Int): String = when {
        value > 0 -> "+$value"
        value < 0 -> "−${-value}"
        else -> "0"
    }

    /** Fator de contraste com 2 casas e vírgula: `1.25` → `"1,25"`. */
    fun factor(value: Float): String = NumberFormat.getNumberInstance(PT_BR).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value.toDouble())

    private const val QUARTER_TURN = 90
}
