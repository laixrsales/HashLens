package br.ufv.hashlens.domain.model

import java.time.Instant

/**
 * Aparelho vinculado a uma carteira (002 data-model §1.1; v2 em 020 data-model §3).
 *
 * @property owner carteira que registrou o aparelho (`0x...`).
 * @property publicKey chave pública P-256 não comprimida, `0x04 ‖ X ‖ Y` (65 bytes).
 * @property registeredAt horário do bloco do vínculo; é o `referenceTime` da revalidação da
 *   atestação na verificação (020 attestation-policy §1).
 * @property attestationHash `keccak256(abi.encode(attestationChain))` gravado pelo contrato.
 * @property registeredBlock bloco do evento `DeviceAttested`, onde a cadeia é buscada.
 */
data class Device(
    val id: Int,
    val owner: String,
    val publicKey: ByteArray,
    val registeredAt: Instant,
    val attestationHash: ByteArray,
    val registeredBlock: Long
) {
    // ByteArray compara por referência em data classes; aqui as chaves e o hash comparam por conteúdo
    override fun equals(other: Any?): Boolean = this === other ||
        other is Device &&
        id == other.id &&
        owner == other.owner &&
        publicKey.contentEquals(other.publicKey) &&
        registeredAt == other.registeredAt &&
        attestationHash.contentEquals(other.attestationHash) &&
        registeredBlock == other.registeredBlock

    override fun hashCode(): Int = listOf(
        id,
        owner,
        publicKey.contentHashCode(),
        registeredAt,
        attestationHash.contentHashCode(),
        registeredBlock
    ).hashCode()
}
