package br.ufv.hashlens.core.crypto

import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Mensagem assinada pela chave do aparelho (signature-payload §1, v1): 195 bytes, campos em
 * ordem fixa, sem separadores, inteiros em big-endian.
 *
 * @property operations string on-chain das operações (hashing-spec §4); vazia para originais.
 */
data class SignaturePayload(
    val chainId: Long,
    val contract: String,
    val registrant: String,
    val deviceId: Int,
    val sha256: Sha256,
    val pHash: PHash,
    val parentId: Long,
    val operations: String
) {
    fun toByteArray(): ByteArray {
        val operationsHash = MessageDigest.getInstance("SHA-256").digest(operations.encodeToByteArray())
        return ByteBuffer.allocate(SIZE) // big-endian por padrão
            .put(DOMAIN)
            .putUint256(chainId, "chainId")
            .put(address(contract, "contract"))
            .put(address(registrant, "registrant"))
            .putInt(deviceId)
            .put(sha256.toByteArray())
            .putLong(pHash.value.toLong())
            .putUint256(parentId, "parentId")
            .put(operationsHash)
            .array()
    }

    companion object {
        const val SIZE = 195

        private val DOMAIN = "HASHLENS-IMG-v1".encodeToByteArray()
        private val ADDRESS = Regex("0x[0-9a-fA-F]{40}")
        private const val UINT256_PADDING = 32 - Long.SIZE_BYTES

        /** Endereço `0x` + 40 hex (maiúsculas do checksum EIP-55 são ignoradas) → 20 bytes. */
        private fun address(value: String, field: String): ByteArray {
            require(ADDRESS.matches(value)) { "$field não é um endereço válido: $value" }
            return value.substring(2).lowercase().hexToByteArray()
        }

        private fun ByteBuffer.putUint256(value: Long, field: String): ByteBuffer {
            require(value >= 0) { "$field não pode ser negativo: $value" }
            return put(ByteArray(UINT256_PADDING)).putLong(value)
        }
    }
}
