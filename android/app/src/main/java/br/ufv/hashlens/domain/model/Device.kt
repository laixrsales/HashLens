package br.ufv.hashlens.domain.model

/**
 * Aparelho vinculado a uma carteira (data-model §1.1 e §3).
 *
 * @property owner carteira que registrou o aparelho (`0x...`).
 * @property publicKey chave pública P-256 não comprimida, `0x04 ‖ X ‖ Y` (65 bytes).
 */
data class Device(val id: Int, val owner: String, val publicKey: ByteArray) {
    // ByteArray compara por referência em data classes; aqui a chave compara por conteúdo
    override fun equals(other: Any?): Boolean = this === other ||
        other is Device &&
        id == other.id &&
        owner == other.owner &&
        publicKey.contentEquals(other.publicKey)

    override fun hashCode(): Int = HASH_PRIME * (HASH_PRIME * id + owner.hashCode()) + publicKey.contentHashCode()

    private companion object {
        const val HASH_PRIME = 31
    }
}
