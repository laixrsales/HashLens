package br.ufv.hashlens.ui.common

import android.content.res.Resources
import br.ufv.hashlens.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Texto do percentual de alteração visual (hashing-spec §3, research R24): sempre em pt-BR, com
 * vírgula decimal e 1 casa, independentemente do locale do aparelho.
 */
object PercentFormat {
    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

    /**
     * @param isEditedVersion `true` para uma versão registrada como edição (`RegisteredEdited`):
     *   com 0%, avisa que o arquivo mesmo assim não é idêntico à original (spec, Edge Cases).
     */
    fun alteration(resources: Resources, percent: Double, isEditedVersion: Boolean = false): String = when {
        percent != 0.0 -> resources.getString(R.string.alteration_percent, number(percent))
        isEditedVersion -> resources.getString(R.string.alteration_none_edited)
        else -> resources.getString(R.string.alteration_none)
    }

    /** `12.5` → `"12,5"`; `100.0` → `"100,0"`. */
    fun number(percent: Double): String = NumberFormat.getNumberInstance(PT_BR).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(percent)
}
