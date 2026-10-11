package br.ufv.hashlens.work

import androidx.work.WorkManager
import javax.inject.Inject

/** Agenda o acompanhamento em segundo plano dos registros enviados ([PendingTxWorker]). */
fun interface PendingTxScheduler {
    /** Um ciclo assim que houver rede, e o ciclo periódico se ainda não estiver agendado. */
    fun trackPending()
}

class WorkManagerPendingTxScheduler @Inject constructor(private val workManager: WorkManager) : PendingTxScheduler {
    override fun trackPending() {
        PendingTxWorker.runNow(workManager)
        PendingTxWorker.schedulePeriodic(workManager)
    }
}
