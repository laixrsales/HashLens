package br.ufv.hashlens.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.ReceiptTracker
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.RegistrationStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Acompanha em segundo plano os registros enviados até a confirmação ou o prazo (data-model §2.3,
 * FR-014, research R23): a cada ciclo, um passo do [PendingTxReconciler] para cada registro
 * `PENDING` ou `FAILED(TIMEOUT)`.
 *
 * Falha de rede deixa o registro como está para o próximo ciclo, e o ciclo sempre termina com
 * sucesso (o agendamento periódico é a nova tentativa).
 */
@HiltWorker
class PendingTxWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val images: LocalImageDao,
    private val receipts: ReceiptTracker,
    private val reader: RegistryReader,
    private val config: ChainConfig,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {
    private val reconciler = PendingTxReconciler(images, receipts, reader, config, clock)

    override suspend fun doWork(): Result {
        images.getByStatus(listOf(RegistrationStatus.PENDING, RegistrationStatus.FAILED))
            .forEach { image ->
                try {
                    reconciler.check(image)
                } catch (_: RegistryException.Network) {
                    // Sem rede: fica como está até o próximo ciclo
                }
            }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "pending-tx"
        private const val ONE_TIME_NAME = "pending-tx-now"
        private const val PERIOD_MINUTES = 15L

        private val NETWORK = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Ciclo periódico (mínimo do WorkManager: 15 min); mantém o agendamento existente. */
        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<PendingTxWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                .setConstraints(NETWORK)
                .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Um ciclo assim que houver rede (ex.: depois de enviar uma transação ou abrir o acervo). */
        fun runNow(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<PendingTxWorker>().setConstraints(NETWORK).build()
            workManager.enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
