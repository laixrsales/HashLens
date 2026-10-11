package br.ufv.hashlens.ui.capture

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.ui.components.TechnicalItem
import br.ufv.hashlens.work.PendingTxReconciler
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay

/** Etapa final de um registro acompanhado com a tela aberta, com os detalhes técnicos se confirmado. */
data class WatchedStage(val stage: RegistrationStage, val technical: List<TechnicalItem> = emptyList())

/**
 * Acompanhamento do registro com a tela aberta (captura e edição): consulta a rede pelo
 * [PendingTxReconciler] até a imagem sair de `PENDING`. Fora da tela, o `PendingTxWorker` segue o
 * mesmo caminho.
 */
class RegistrationWatcher(
    private val images: LocalImageDao,
    private val reconciler: PendingTxReconciler,
    private val reader: RegistryReader,
    private val config: ChainConfig,
    private val clock: Clock
) {
    /** Confirmado ou com falha; `null` se a imagem saiu do acervo. Cancelável entre as consultas. */
    suspend fun watch(localId: Long): WatchedStage? {
        while (true) {
            val image = images.getById(localId) ?: return null
            finalStage(image)?.let { return it }
            try {
                reconciler.check(image)
            } catch (_: RegistryException.Network) {
                // Sem rede agora; o próximo ciclo tenta de novo
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /** Confirmado ou com falha; `null` enquanto o registro está em andamento. */
    private suspend fun finalStage(image: LocalImageEntity): WatchedStage? = when (image.status) {
        RegistrationStatus.CONFIRMED -> confirmed(image)
        RegistrationStatus.FAILED -> WatchedStage(RegistrationStage.Failed(image.failureReason.toFailure()))
        else -> null
    }

    /** "Foto registrada" com o horário do bloco (sem rede, o do aparelho) e os detalhes técnicos. */
    suspend fun confirmed(image: LocalImageEntity): WatchedStage {
        val registeredAt = image.recordId?.let { registrationTime(it) } ?: Instant.ofEpochMilli(clock.millis())
        return WatchedStage(RegistrationStage.Confirmed(registeredAt), technicalItems(image, config))
    }

    /** Horário do bloco que confirmou; sem rede, `null`. */
    private suspend fun registrationTime(recordId: Long): Instant? = try {
        reader.getRecord(recordId)?.timestamp
    } catch (_: RegistryException) {
        null
    }

    companion object {
        /** Um bloco da Sepolia sai a cada ~12 s. */
        const val POLL_INTERVAL_MS = 4_000L
    }
}
