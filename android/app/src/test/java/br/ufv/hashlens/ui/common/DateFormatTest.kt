package br.ufv.hashlens.ui.common

import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Test

class DateFormatTest {
    @Test
    fun `data e hora em pt-BR no fuso informado`() {
        DateFormat.dateTime(Instant.parse("2026-10-04T17:32:00Z"), ZoneId.of("America/Sao_Paulo")) shouldBe
            "4 out. 2026, 14:32"
    }
}
