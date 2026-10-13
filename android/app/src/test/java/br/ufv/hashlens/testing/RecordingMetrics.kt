package br.ufv.hashlens.testing

import br.ufv.hashlens.core.metrics.Metric
import br.ufv.hashlens.core.metrics.MetricsLogger
import java.time.Duration

/** [MetricsLogger] de teste: guarda as medidas em memória. */
class RecordingMetrics : MetricsLogger {
    data class Record(val metric: Metric, val duration: Duration, val outcome: String)

    val records = mutableListOf<Record>()

    override fun record(metric: Metric, duration: Duration, outcome: String) {
        records += Record(metric, duration, outcome)
    }
}
