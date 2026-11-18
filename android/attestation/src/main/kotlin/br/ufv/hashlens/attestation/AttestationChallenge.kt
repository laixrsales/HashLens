package br.ufv.hashlens.attestation

import java.math.BigInteger
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

/**
 * Desafio da atestação (`contracts/attestation-policy.md` §2): 95 bytes, inteiros em big-endian,
 * `HASHLENS-ATT-v1 ‖ chainId (32 B) ‖ contract (20 B) ‖ wallet (20 B) ‖ issuedAt (8 B)`.
 *
 * O app monta o desafio ao gerar a chave (research R6); o atestador e a verificação leem de volta
 * da extensão da folha e conferem com o esperado. Endereços ficam em hex minúsculo com `0x`.
 */
class AttestationChallenge(val chainId: Long, contract: String, wallet: String, val issuedAt: Instant) {
    val contract: String = normalizeAddress(contract)
    val wallet: String = normalizeAddress(wallet)

    init {
        require(chainId >= 0) { "chainId negativo" }
        require(issuedAt.epochSecond >= 0) { "issuedAt antes de 1970" }
    }

    fun encode(): ByteArray = ByteBuffer.allocate(SIZE).apply {
        put(DOMAIN_BYTES)
        put(uint256(chainId))
        put(addressBytes(contract))
        put(addressBytes(wallet))
        putLong(issuedAt.epochSecond)
    }.array()

    /** `referenceTime − window.before ≤ issuedAt ≤ referenceTime + window.after`, bordas incluídas. */
    fun isWithin(window: ChallengeWindow, referenceTime: Instant): Boolean =
        !issuedAt.isBefore(referenceTime.minus(window.before)) && !issuedAt.isAfter(referenceTime.plus(window.after))

    /** Mesma rede, contrato e carteira (campos 1 a 4 da policy §2). */
    fun matches(chainId: Long, contract: String, wallet: String): Boolean = this.chainId == chainId &&
        this.contract == normalizeAddress(contract) &&
        this.wallet == normalizeAddress(wallet)

    override fun equals(other: Any?): Boolean = other is AttestationChallenge &&
        chainId == other.chainId &&
        contract == other.contract &&
        wallet == other.wallet &&
        issuedAt == other.issuedAt

    override fun hashCode(): Int = listOf(chainId, contract, wallet, issuedAt).hashCode()

    override fun toString(): String =
        "AttestationChallenge(chainId=$chainId, contract=$contract, wallet=$wallet, issuedAt=$issuedAt)"

    companion object {
        const val DOMAIN = "HASHLENS-ATT-v1"
        const val SIZE = 95
        private val DOMAIN_BYTES = DOMAIN.toByteArray(Charsets.US_ASCII)
        private const val UINT256_SIZE = 32
        private const val ADDRESS_SIZE = 20
        private const val ADDRESS_HEX_LENGTH = 40
        private const val HEX_DIGITS_PER_BYTE = 2
        private const val HEX_RADIX = 16
        private val ADDRESS_HEX = Regex("[0-9a-f]{$ADDRESS_HEX_LENGTH}")

        /** Lê um desafio; `null` se o tamanho ou o domínio não forem os da v1. */
        fun decode(bytes: ByteArray): AttestationChallenge? {
            val buffer = ByteBuffer.wrap(bytes).takeIf { bytes.size == SIZE } ?: return null
            val domain = ByteArray(DOMAIN_BYTES.size).also(buffer::get)
            val chainId = BigInteger(1, ByteArray(UINT256_SIZE).also(buffer::get))
            val contract = ByteArray(ADDRESS_SIZE).also(buffer::get)
            val wallet = ByteArray(ADDRESS_SIZE).also(buffer::get)
            val issuedAt = buffer.long
            val valid = domain.contentEquals(DOMAIN_BYTES) && chainId.bitLength() < Long.SIZE_BITS && issuedAt >= 0
            return if (valid) {
                AttestationChallenge(chainId.toLong(), hex(contract), hex(wallet), Instant.ofEpochSecond(issuedAt))
            } else {
                null
            }
        }

        /** `0x` + 40 dígitos hex minúsculos; aceita com ou sem `0x`, em qualquer caixa. */
        fun normalizeAddress(address: String): String {
            val digits = address.removePrefix("0x").removePrefix("0X").lowercase()
            require(ADDRESS_HEX.matches(digits)) { "endereço inválido: $address" }
            return "0x$digits"
        }

        private fun addressBytes(address: String): ByteArray =
            address.removePrefix("0x").chunked(HEX_DIGITS_PER_BYTE).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()

        private fun uint256(value: Long): ByteArray =
            ByteBuffer.allocate(UINT256_SIZE).putLong(UINT256_SIZE - Long.SIZE_BYTES, value).array()

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}

/** Janela de `issuedAt` em torno de `referenceTime` em cada contexto (policy §2). */
enum class ChallengeWindow(beforeMinutes: Long, afterMinutes: Long) {
    /** No atestador, `referenceTime = agora`. */
    ATTESTER(beforeMinutes = 15, afterMinutes = 2),

    /** Na verificação, `referenceTime = device.registeredAt`: soma dos dois prazos de 15 min. */
    VERIFICATION(beforeMinutes = 30, afterMinutes = 2);

    val before: Duration = Duration.ofMinutes(beforeMinutes)
    val after: Duration = Duration.ofMinutes(afterMinutes)
}
