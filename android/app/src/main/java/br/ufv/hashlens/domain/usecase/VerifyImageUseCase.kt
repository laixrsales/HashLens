package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.core.hashing.Hamming
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.di.DefaultDispatcher
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.domain.model.VerificationResult
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.domain.model.toView
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verificação pública de uma imagem, sem carteira e sem custo (verification-result §1; spec FR-019
 * a FR-023): primeiro pelo SHA-256 exato, depois pelo pHash, validando a assinatura do aparelho de
 * cada registro exibido.
 *
 * A assinatura é conferida com o aparelho gravado no próprio registro ([ImageRecord.deviceId]),
 * não com o aparelho atual da carteira: registros de um aparelho antigo continuam válidos (R25).
 */
class VerifyImageUseCase @Inject constructor(
    private val sha256Hasher: Sha256Hasher,
    private val perceptualHasher: PerceptualHasher,
    private val reader: RegistryReader,
    private val signatureVerifier: DeviceSignatureVerifier,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * @param bytes conteúdo exato do arquivo escolhido.
     * @param onQuerying avisado quando a imagem já foi lida e começam as consultas ao contrato.
     */
    suspend operator fun invoke(bytes: ByteArray, onQuerying: () -> Unit = {}): VerificationResult {
        // O pHash decodifica a imagem: calculado já no passo 1, serve de teste de formato sem decodificar duas vezes
        val pHash = withContext(defaultDispatcher) {
            try {
                perceptualHasher.hash(bytes)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: return VerificationResult.Error(Reason.UNSUPPORTED_FILE)
        val sha256 = withContext(defaultDispatcher) { sha256Hasher.hash(bytes.inputStream()) }
        onQuerying()

        return classifying { byExactHash(sha256) ?: byPerceptualHash(pHash) }
    }

    /**
     * Resultado de um registro já conhecido pelo [id] (ex.: "Ver registro" quando a foto já estava
     * registrada): original, versão alterada ou adulterado, com a mesma validação da assinatura.
     */
    suspend fun verifyRecord(id: Long): VerificationResult = classifying { byId(id) }

    private suspend fun classifying(block: suspend Lookup.() -> VerificationResult): VerificationResult = try {
        Lookup().block()
    } catch (_: RegistryException.Network) {
        VerificationResult.Error(Reason.NETWORK)
    } catch (_: RegistryException) {
        VerificationResult.Error(Reason.UNKNOWN)
    } catch (_: InconsistentRegistryException) {
        VerificationResult.Error(Reason.UNKNOWN)
    }

    /** Consultas de uma verificação, com os registros já lidos guardados (várias versões partilham a original). */
    private inner class Lookup {
        private val records = mutableMapOf<Long, ImageRecord>()

        /** Passos 2 a 4; `null` se o SHA-256 não está registrado. */
        suspend fun byExactHash(sha256: Sha256): VerificationResult? = reader.getIdBySha256(sha256)?.let { byId(it) }

        /** Passo 4: assinatura, depois original ou versão alterada. */
        suspend fun byId(id: Long): VerificationResult {
            val record = record(id)
            return when {
                !hasValidSignature(record) -> VerificationResult.Tampered(record.toView())

                record.isOriginal -> VerificationResult.RegisteredOriginal(record.toView())

                else -> {
                    val original = record(record.originalId)
                    VerificationResult.RegisteredEdited(
                        record = record.toView(),
                        original = original.toView(),
                        percentFromOriginal = Hamming.percent(record.pHash, original.pHash)
                    )
                }
            }
        }

        /** Passos 5 a 8: só registros com assinatura válida entram; se nenhum, adulterado do primeiro. */
        suspend fun byPerceptualHash(pHash: PHash): VerificationResult {
            val matches = reader.getIdsByPHash(pHash).map { record(it) }.sortedBy { it.timestamp }
            val valid = matches.filter { hasValidSignature(it) }
            return when {
                matches.isEmpty() -> VerificationResult.NotRegistered
                valid.isEmpty() -> VerificationResult.Tampered(matches.first().toView())
                else -> VerificationResult.VisualMatch(valid.map { candidate(it) })
            }
        }

        private suspend fun candidate(record: ImageRecord): VerificationResult.VisualMatch.Candidate {
            val original = if (record.isOriginal) record else record(record.originalId)
            return VerificationResult.VisualMatch.Candidate(
                record = record.toView(),
                original = original.toView(),
                percentFromOriginal = Hamming.percent(record.pHash, original.pHash)
            )
        }

        private suspend fun record(id: Long): ImageRecord = records[id]
            ?: (reader.getRecord(id) ?: throw InconsistentRegistryException("registro $id não existe"))
                .also { records[id] = it }

        // Aparelho inexistente: o contrato não aceitaria o registro, então os dados não são confiáveis
        private suspend fun hasValidSignature(record: ImageRecord): Boolean =
            reader.getDevice(record.deviceId)?.let { signatureVerifier.verify(record, it) } ?: false
    }

    /** O contrato apontou para um registro que não devolve (ex.: nó desatualizado). */
    private class InconsistentRegistryException(message: String) : Exception(message)
}
