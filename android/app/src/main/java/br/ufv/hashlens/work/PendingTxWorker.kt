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
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.domain.model.Sha256
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Acompanha os registros enviados até a confirmação ou o prazo (data-model §2.3, FR-014, research R23).
 *
 * A cada ciclo:
 * - `PENDING` com recibo de sucesso → `CONFIRMED` com o `recordId` do evento `ImageRegistered`;
 * - `PENDING` com recibo revertido, ou sem recibo depois de [ChainConfig.pendingTimeout] desde
 *   `submittedAt` → reconciliação por `getIdBySha256`: hash on-chain → `CONFIRMED` com esse id;
 *   senão `FAILED(REVERTED)` ou `FAILED(TIMEOUT)`;
 * - `FAILED(TIMEOUT)` → reconciliação de novo, porque a transação ainda pode ser minerada.
 *
 * O hash é a identidade do registro, não o `txHash`: nenhum registro vai a `FAILED` sem consultar
 * `getIdBySha256`. Falha de rede deixa o registro como está para o próximo ciclo, e o ciclo sempre
 * termina com sucesso (o agendamento periódico é a nova tentativa).
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
    override suspend fun doWork(): Result {
        images.getByStatus(listOf(RegistrationStatus.PENDING, RegistrationStatus.FAILED))
            .forEach { image ->
                try {
                    when {
                        image.status == RegistrationStatus.PENDING -> track(image)
                        image.failureReason == FailureReason.TIMEOUT -> reconcile(image, failure = null)
                    }
                } catch (_: RegistryException.Network) {
                    // Sem rede: fica como está até o próximo ciclo
                }
            }
        return Result.success()
    }

    private suspend fun track(image: LocalImageEntity) {
        val receipt = image.txHash?.let { receipts.receipt(it) }
        when {
            receipt?.success == true && receipt.recordId != null -> confirm(image, receipt.recordId)
            receipt != null -> reconcile(image, failure = FailureReason.REVERTED.takeUnless { receipt.success })
            timedOut(image) -> reconcile(image, failure = FailureReason.TIMEOUT)
        }
    }

    /**
     * Confirma pelo hash on-chain; sem ele, grava [failure] (nulo: mantém o estado atual).
     *
     * @throws RegistryException.Network sem conexão com o nó.
     */
    private suspend fun reconcile(image: LocalImageEntity, failure: FailureReason?) {
        val recordId = reader.getIdBySha256(Sha256(image.sha256Hex))
        when {
            recordId != null -> confirm(image, recordId)
            failure != null -> images.update(image.copy(status = RegistrationStatus.FAILED, failureReason = failure))
        }
    }

    private suspend fun confirm(image: LocalImageEntity, recordId: Long) {
        images.update(
            image.copy(
                status = RegistrationStatus.CONFIRMED,
                recordId = recordId,
                // Captura é a raiz da própria genealogia; edições já trazem a raiz do pai
                originalRecordId = if (image.origin == ImageOrigin.CAPTURE) recordId else image.originalRecordId,
                failureReason = null,
                errorMessage = null
            )
        )
    }

    private fun timedOut(image: LocalImageEntity): Boolean {
        val submittedAt = image.submittedAt ?: return true
        return clock.millis() - submittedAt >= config.pendingTimeout.toMillis()
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
