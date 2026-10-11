package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.core.hashing.Hamming
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.domain.model.GenealogyNode
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.domain.model.toView
import javax.inject.Inject
import kotlin.math.min

/** Resultado de reconstruir a genealogia de um registro. */
sealed interface GenealogyResult {
    data class Traced(val genealogy: Genealogy) : GenealogyResult

    /** Não foi possível consultar o contrato, ou ele apontou para um registro que não devolve. */
    data class Error(val reason: Reason) : GenealogyResult
}

/**
 * Genealogia de um registro (verification-result §4; US5, FR-027, FR-028): sobe por `parentId` até a
 * original (o caminho, sempre completo) e desce por `getChildren` a partir dela (a árvore), em
 * largura, até [Genealogy.MAX_LEVELS] níveis e [Genealogy.MAX_NODES] nós.
 *
 * Cada nó traz a alteração em relação ao pai e à original e se a assinatura do aparelho confere,
 * conferida com o aparelho gravado no próprio registro (R25). Assinatura inválida não tira o nó da
 * árvore: ele aparece sinalizado.
 */
class TraceGenealogyUseCase @Inject constructor(
    private val reader: RegistryReader,
    private val signatureVerifier: DeviceSignatureVerifier
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

        suspend fun of(recordId: Long): Genealogy {
            val path = pathTo(record(recordId))
            val original = path.first()
            loadTree(original)
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

        /** Desce em largura a partir da original, até os limites de níveis e de nós. */
        private suspend fun loadTree(original: ImageRecord) {
            var level = listOf(original)
            var depth = 1
            var count = 1
            while (level.isNotEmpty()) {
                val next = mutableListOf<ImageRecord>()
                for (parent in level) {
                    val ids = reader.getChildren(parent.id)
                    val allowed = if (depth >= Genealogy.MAX_LEVELS) 0 else min(ids.size, Genealogy.MAX_NODES - count)
                    if (allowed < ids.size) truncated = true
                    val loaded = ids.take(allowed).map { record(it) }
                    children[parent.id] = loaded
                    next += loaded
                    count += loaded.size
                }
                level = next
                depth++
            }
        }

        private suspend fun node(record: ImageRecord, parent: ImageRecord?, original: ImageRecord): GenealogyNode =
            GenealogyNode(
                record = record.toView(),
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
