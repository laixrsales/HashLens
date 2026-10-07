package br.ufv.hashlens.domain.model

import java.time.Instant

/**
 * Registro de imagem como está on-chain (data-model §1.2 e §3).
 *
 * @property parentId `null` para originais (on-chain = 0).
 * @property originalId raiz da cadeia; igual a [id] para originais.
 * @property signature assinatura do aparelho em `r ‖ s` (64 bytes).
 * @property operations texto on-chain exato (hashing-spec §4), vazio para originais. Fica sem
 *   decodificar porque é coberto pela assinatura do aparelho (signature-payload §1); a lista
 *   decodificada fica em [RecordView.operations].
 */
data class ImageRecord(
    val id: Long,
    val sha256: Sha256,
    val pHash: PHash,
    val parentId: Long?,
    val originalId: Long,
    val registrant: String,
    val deviceId: Int,
    val signature: ByteArray,
    val timestamp: Instant,
    val operations: String
) {
    val isOriginal: Boolean get() = parentId == null

    // ByteArray compara por referência em data classes; aqui a assinatura compara por conteúdo
    override fun equals(other: Any?): Boolean = this === other ||
        other is ImageRecord &&
        id == other.id &&
        sha256 == other.sha256 &&
        pHash == other.pHash &&
        parentId == other.parentId &&
        originalId == other.originalId &&
        registrant == other.registrant &&
        deviceId == other.deviceId &&
        signature.contentEquals(other.signature) &&
        timestamp == other.timestamp &&
        operations == other.operations

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = HASH_PRIME * result + sha256.hashCode()
        result = HASH_PRIME * result + signature.contentHashCode()
        return result
    }

    private companion object {
        const val HASH_PRIME = 31
    }
}
