package br.ufv.hashlens.work

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.ReceiptTracker
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.domain.model.Sha256
import java.time.Clock
import javax.inject.Inject

/**
 * Um passo do acompanhamento de um registro enviado (data-model §2.3, FR-014, research R23), usado
 * pelo [PendingTxWorker] em segundo plano e pela tela de Acompanhamento enquanto está aberta.
 *
 * - `PENDING` com recibo de sucesso → `CONFIRMED` com o `recordId` do evento `ImageRegistered`;
 * - `PENDING` com recibo revertido, ou sem recibo depois de [ChainConfig.pendingTimeout] desde
 *   `submittedAt` → reconciliação por `getIdBySha256`: hash on-chain → `CONFIRMED` com esse id;
 *   senão `FAILED(REVERTED)` ou `FAILED(TIMEOUT)`;
 * - `FAILED(TIMEOUT)` → reconciliação de novo, porque a transação ainda pode ser minerada.
 *
 * O hash é a identidade do registro, não o `txHash`: nenhum registro vai a `FAILED` sem consultar
 * `getIdBySha256`.
 */
class PendingTxReconciler @Inject constructor(
    private val images: LocalImageDao,
    private val receipts: ReceiptTracker,
    private val reader: RegistryReader,
    private val config: ChainConfig,
    private val clock: Clock
) {
    /**
     * Avança [image] se houver novidade; outros estados ficam como estão.
     *
     * @throws br.ufv.hashlens.data.chain.RegistryException.Network sem conexão com o nó.
     */
    suspend fun check(image: LocalImageEntity) {
        when {
            image.status == RegistrationStatus.PENDING -> track(image)
            image.status == RegistrationStatus.FAILED && image.failureReason == FailureReason.TIMEOUT ->
                reconcile(image, failure = null)
        }
    }

    private suspend fun track(image: LocalImageEntity) {
        val receipt = image.txHash?.let { receipts.receipt(it) }
        when {
            receipt?.success == true && receipt.recordId != null -> confirm(image, receipt.recordId)
            receipt != null -> reconcile(image, failure = FailureReason.REVERTED.takeUnless { receipt.success })
            timedOut(image) -> reconcile(image, failure = FailureReason.TIMEOUT)
        }
    }

    /** Confirma pelo hash on-chain; sem ele, grava [failure] (nulo: mantém o estado atual). */
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
}
