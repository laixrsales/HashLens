package br.ufv.hashlens.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Data e hora na camada principal (ux.md): `4 out. 2026, 14:32`, sempre em pt-BR (R24). */
object DateFormat {
    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("pt-BR"))

    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME.format(instant.atZone(zone))
}
