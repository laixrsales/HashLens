package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.core.hashing.Hamming
import br.ufv.hashlens.data.chain.RegistrationTxFinder
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.domain.model.GenealogyNode
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.domain.model.toView
import javax.inject.Inject

/** Resultado de reconstruir a genealogia de um registro. */
sealed interface GenealogyResult {
    data class Traced(val genealogy: Genealogy) : GenealogyResult

    /** Não foi possível consultar o contrato, ou ele apontou para um registro que não devolve. */
    data class Error(val reason: Reason) : GenealogyResult
}

/**
 * Genealogia de um registro (verification-result §4; US5, FR-027, FR-028): sobe por `parentId` até a
 * original (o caminho, sempre completo) e desce por `getChildren` a partir dela (a árvore), em
 * largura, até [Genealogy.MAX_LEVELS] níveis e [Genealogy.MAX_NODES] nós. Os nós do caminho ficam
 * sempre na árvore, mesmo além dos limites: a tela mostra a linha do tempo até o registro consultado.
 *
 * Cada nó traz a alteração em relação ao pai e à original e se a assinatura do aparelho confere,
 * conferida com o aparelho gravado no próprio registro (R25). Assinatura inválida não tira o nó da
 * árvore: ele aparece sinalizado.
 *
 * O link da transação de cada nó vem dos eventos `ImageRegistered` da árvore ([RegistrationTxFinder]),
 * porque o contrato não guarda a transação de um registro. Se essa consulta falhar, as versões
 * registradas neste aparelho usam a transação do acervo local; as demais ficam sem link, e a
 * genealogia não falha por isso.
 */
class TraceGenealogyUseCase @Inject constructor(
    private val reader: RegistryReader,
    private val signatureVerifier: DeviceSignatureVerifier,
    private val txFinder: RegistrationTxFinder,
    private val images: LocalImageDao,
    private val config: ChainConfig
) {
    suspend operator fun invoke(recordId: Long): GenealogyResult = try {
        GenealogyResult.Traced(Trace().of(recordId))
    } catch (_: RegistryException.Network) {
        GenealogyResult.Error(Reason.NETWORK)
    } catch (_: RegistryException) {
        GenealogyResult.Error(Reason.UNKNOWN)
    } catch (_: InconsistentRegistryException) {
        GenealogyResult.Error(Reason.UNKNOWN)
    }

    /** Uma reconstrução: os registros e aparelhos lidos ficam guardados só durante ela. */
    private inner class Trace {
        private val records = mutableMapOf<Long, ImageRecord>()
        private val devices = mutableMapOf<Int, Device?>()
        private val children = mutableMapOf<Long, List<ImageRecord>>()
        private var truncated = false
        private var txHashes = emptyMap<Long, String>()

        suspend fun of(recordId: Long): Genealogy {
            val path = pathTo(record(recordId))
            val original = path.first()
            loadTree(original, onPath = path.mapTo(mutableSetOf()) { it.id })
            txHashes = txHashes(original.id)
            return Genealogy(
                root = node(original, parent = null, original = original),
                pathToQueried = path.map { it.id },
                truncated = truncated
            )
        }

        /** Da original até [queried], subindo por `parentId`. */
        private suspend fun pathTo(queried: ImageRecord): List<ImageRecord> {
            val path = ArrayDeque(listOf(queried))
            var parentId = queried.parentId
            while (parentId != null) {
                val parent = record(parentId)
                path.addFirst(parent)
                parentId = parent.parentId
            }
            return path
        }

        /**
         * Desce em largura a partir da original. Os nós do caminho entram sempre (a linha do tempo
         * precisa deles); os limites de níveis e de nós valem para as demais versões.
         */
        private suspend fun loadTree(original: ImageRecord, onPath: Set<Long>) {
            var level = listOf(original)
            var depth = 1
            var count = 1
            while (level.isNotEmpty()) {
                val next = mutableListOf<ImageRecord>()
                for (parent in level) {
                    val ids = reader.getChildren(parent.id)
                    val pathIds = ids.filter { it in onPath }
                    val others = ids.filterNot { it in onPath }
                    val room = if (depth >= Genealogy.MAX_LEVELS) 0 else Genealogy.MAX_NODES - count - pathIds.size
                    val kept = others.take(room.coerceIn(0, others.size)).toSet() + pathIds
                    if (kept.size < ids.size) truncated = true
                    // Na ordem de registro, como o contrato devolve
                    val loaded = ids.filter { it in kept }.map { record(it) }
                    children[parent.id] = loaded
                    next += loaded
                    count += loaded.size
                }
                level = next
                depth++
            }
        }

        /** Transação de cada registro carregado: eventos primeiro, acervo local para o que faltar. */
        private suspend fun txHashes(originalId: Long): Map<Long, String> {
            val fromEvents = try {
                txFinder.byOriginal(originalId)
            } catch (_: RegistryException) {
                emptyMap()
            }
            val missing = records.keys - fromEvents.keys
            if (missing.isEmpty()) return fromEvents
            val fromDevice = images.getConfirmedByRecordIds(missing.toList())
                .mapNotNull { image -> image.recordId?.let { id -> image.txHash?.let { id to it } } }
                .toMap()
            return fromDevice + fromEvents
        }

        private suspend fun node(record: ImageRecord, parent: ImageRecord?, original: ImageRecord): GenealogyNode =
            GenealogyNode(
                record = record.toView().copy(txUrl = txHashes[record.id]?.let(config::transactionUrl)),
                percentFromParent = parent?.let { Hamming.percent(record.pHash, it.pHash) },
                percentFromOriginal = Hamming.percent(record.pHash, original.pHash),
                signatureValid = hasValidSignature(record),
                children = children[record.id].orEmpty().map { node(it, parent = record, original = original) }
            )

        private suspend fun record(id: Long): ImageRecord = records[id]
            ?: (reader.getRecord(id) ?: throw InconsistentRegistryException("registro $id não existe"))
                .also { records[id] = it }

        // Aparelho inexistente: o contrato não aceitaria o registro, então os dados não são confiáveis
        private suspend fun hasValidSignature(record: ImageRecord): Boolean {
            val device = if (record.deviceId in devices) {
                devices[record.deviceId]
            } else {
                reader.getDevice(record.deviceId).also { devices[record.deviceId] = it }
            }
            return device?.let { signatureVerifier.verify(record, it) } ?: false
        }
    }

    /** O contrato apontou para um registro que não devolve (ex.: nó desatualizado). */
    private class InconsistentRegistryException(message: String) : Exception(message)
}
