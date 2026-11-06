package br.ufv.hashlens.core.metrics

import br.ufv.hashlens.testing.MutableClock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * T096: métricas para o TCC (SC-007, SC-008) num CSV que se puxa do aparelho. Cada linha é uma medida:
 * quando terminou, qual, quanto durou e como terminou. Nada de pixels, hashes ou endereços.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CsvMetricsLoggerTest {
    @TempDir
    lateinit var dir: File

    private val clock = MutableClock(Instant.parse("2026-10-12T15:00:00Z"))

    private fun lines() = File(dir, CsvMetricsLogger.FILE).readLines()

    @Test
    fun `grava o cabeçalho uma vez e uma linha por medida`() = runTest {
        val logger = CsvMetricsLogger(dir, clock, UnconfinedTestDispatcher(testScheduler), backgroundScope)

        logger.record(Metric.VERIFICATION, Duration.ofMillis(1_234), "RegisteredOriginal")
        clock.advance(Duration.ofSeconds(5))
        logger.record(Metric.CONFIRMATION, Duration.ofSeconds(14), "CONFIRMED")

        lines() shouldContainExactly listOf(
            "momento,medida,duracao_ms,resultado",
            "2026-10-12T15:00:00Z,verificacao,1234,RegisteredOriginal",
            "2026-10-12T15:00:05Z,confirmacao_rede,14000,CONFIRMED"
        )
    }

    @Test
    fun `continua o arquivo de uma abertura anterior, sem repetir o cabeçalho`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        CsvMetricsLogger(
            dir,
            clock,
            dispatcher,
            backgroundScope
        ).record(Metric.CAPTURE_TO_SUBMITTED, Duration.ofSeconds(20), "Submitted")

        CsvMetricsLogger(
            dir,
            clock,
            dispatcher,
            backgroundScope
        ).record(Metric.EDIT_TO_SUBMITTED, Duration.ofSeconds(9), "Submitted")

        lines() shouldContainExactly listOf(
            "momento,medida,duracao_ms,resultado",
            "2026-10-12T15:00:00Z,captura_ate_envio,20000,Submitted",
            "2026-10-12T15:00:00Z,edicao_ate_envio,9000,Submitted"
        )
    }

    @Test
    fun `resultado com vírgula ou aspas vai entre aspas`() = runTest {
        val logger = CsvMetricsLogger(dir, clock, UnconfinedTestDispatcher(testScheduler), backgroundScope)

        logger.record(Metric.VERIFICATION, Duration.ofMillis(10), "Error,\"NETWORK\"")

        lines().last() shouldBe "2026-10-12T15:00:00Z,verificacao,10,\"Error,\"\"NETWORK\"\"\""
    }
}
