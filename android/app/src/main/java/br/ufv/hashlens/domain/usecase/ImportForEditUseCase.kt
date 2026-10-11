package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.di.DefaultDispatcher
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Registro que um arquivo importado reproduz exatamente: o pai da versão editada (US4).
 *
 * @property registrant carteira que registrou o pai (autor da original ou de uma edição).
 * @property originalRegistrant carteira que registrou a original da cadeia.
 * @property bytes conteúdo exato do arquivo importado, base da edição.
 */
data class ImportedParent(
    val recordId: Long,
    val originalId: Long,
    val registrant: String,
    val originalRegistrant: String,
    val bytes: ByteArray
) {
    // ByteArray compara por referência em data classes; aqui os bytes comparam por conteúdo
    override fun equals(other: Any?): Boolean = this === other ||
        other is ImportedParent &&
        recordId == other.recordId &&
        originalId == other.originalId &&
        registrant == other.registrant &&
        originalRegistrant == other.originalRegistrant &&
        bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = HASH_PRIME * recordId.hashCode() + bytes.contentHashCode()

    private companion object {
        const val HASH_PRIME = 31
    }
}

/** Resultado de importar um arquivo para edição. */
sealed interface ImportResult {
    /** Cópia exata de um registro com assinatura válida: o editor abre com ele como pai. */
    data class Eligible(val parent: ImportedParent) : ImportResult

    /** Nenhum registro com este SHA-256, nem que haja correspondência visual (FR-015, R18). */
    data object NotRegistered : ImportResult

    /** O registro existe, mas a assinatura do aparelho não confere (FR-022). */
    data class Tampered(val recordId: Long) : ImportResult

    /** Não foi possível consultar o contrato; nunca é apresentado como recusa. */
    data class Error(val reason: Reason) : ImportResult
}

/**
 * Importar para editar (US4, FR-015, research R18): libera o editor só para uma cópia exata (SHA-256)
 * de um registro, que vira o pai inequívoco da versão. Correspondência só visual é recusada, então o
 * pHash nem é consultado.
 *
 * A assinatura do aparelho do pai é conferida com o aparelho gravado no próprio registro, como na
 * verificação (R25). A original aparece só como autoria e não decide a elegibilidade.
 */
class ImportForEditUseCase @Inject constructor(
    private val sha256Hasher: Sha256Hasher,
    private val reader: RegistryReader,
    private val signatureVerifier: DeviceSignatureVerifier,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /** @param bytes conteúdo exato do arquivo escolhido no Photo Picker. */
    suspend operator fun invoke(bytes: ByteArray): ImportResult {
        val sha256 = withContext(defaultDispatcher) { sha256Hasher.hash(bytes.inputStream()) }
        return try {
            reader.getIdBySha256(sha256)?.let { parent(it, bytes) } ?: ImportResult.NotRegistered
        } catch (_: RegistryException.Network) {
            ImportResult.Error(Reason.NETWORK)
        } catch (_: RegistryException) {
            ImportResult.Error(Reason.UNKNOWN)
        } catch (_: InconsistentRegistryException) {
            ImportResult.Error(Reason.UNKNOWN)
        }
    }

    private suspend fun parent(id: Long, bytes: ByteArray): ImportResult {
        val record = record(id)
        if (!hasValidSignature(record)) return ImportResult.Tampered(id)
        val original = if (record.isOriginal) record else record(record.originalId)
        return ImportResult.Eligible(
            ImportedParent(
                recordId = record.id,
                originalId = record.originalId,
                registrant = record.registrant,
                originalRegistrant = original.registrant,
                bytes = bytes
            )
        )
    }

    private suspend fun record(id: Long): ImageRecord =
        reader.getRecord(id) ?: throw InconsistentRegistryException("registro $id não existe")

    // Aparelho inexistente: o contrato não aceitaria o registro, então os dados não são confiáveis
    private suspend fun hasValidSignature(record: ImageRecord): Boolean =
        reader.getDevice(record.deviceId)?.let { signatureVerifier.verify(record, it) } ?: false

    /** O contrato apontou para um registro que não devolve (ex.: nó desatualizado). */
    private class InconsistentRegistryException(message: String) : Exception(message)
}
