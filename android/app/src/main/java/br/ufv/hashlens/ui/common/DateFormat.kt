package br.ufv.hashlens.ui.common

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Data e hora na camada principal (ux.md): `4 out. 2026, 14:32`, sempre em pt-BR (R24). */
object DateFormat {
    private val PT_BR = Locale.forLanguageTag("pt-BR")
    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", PT_BR)
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", PT_BR)

    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME.format(instant.atZone(zone))

    /** Mês e ano, como o patch de segurança: `set. 2026`. */
    fun monthYear(value: YearMonth): String = MONTH_YEAR.format(value)
}
