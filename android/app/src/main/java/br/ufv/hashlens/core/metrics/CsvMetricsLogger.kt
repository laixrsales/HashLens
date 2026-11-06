package br.ufv.hashlens.core.metrics

import java.io.File
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * [MetricsLogger] que acrescenta uma linha por medida a `metricas.csv` em [directory], fora da thread
 * principal e na ordem em que as medidas chegam. O arquivo continua entre aberturas do app; no
 * aparelho fica em `/sdcard/Android/data/br.ufv.hashlens/files/metrics/` (`adb pull`, ver quickstart).
 *
 * Colunas: `momento` (fim da medida, ISO-8601 UTC), `medida` ([Metric.csvName]), `duracao_ms`, `resultado`.
 */
class CsvMetricsLogger(
    private val directory: File,
    private val clock: Clock,
    ioDispatcher: CoroutineDispatcher,
    scope: CoroutineScope
) : MetricsLogger {
    // Fila com um único consumidor: uma escrita por vez, na ordem de chegada
    private val lines = Channel<String>(Channel.UNLIMITED)

    val file: File get() = File(directory, FILE)

    init {
        scope.launch(ioDispatcher) {
            for (line in lines) {
                directory.mkdirs()
                if (!file.exists() || file.length() == 0L) file.writeText("$HEADER\n")
                file.appendText("$line\n")
            }
        }
    }

    override fun record(metric: Metric, duration: Duration, outcome: String) {
        val line = listOf(clock.instant().toString(), metric.csvName, duration.toMillis().toString(), escape(outcome))
            .joinToString(",")
        lines.trySend(line)
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value

    companion object {
        const val FILE = "metricas.csv"
        const val HEADER = "momento,medida,duracao_ms,resultado"
    }
}
