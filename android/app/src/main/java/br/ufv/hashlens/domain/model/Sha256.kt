package br.ufv.hashlens.domain.model

/** SHA-256 de um arquivo (hashing-spec §1): 64 caracteres hex minúsculos; on-chain como `bytes32`. */
@JvmInline
value class Sha256(val hex: String) {
    init {
        require(HEX.matches(hex)) { "SHA-256 deve ter 64 caracteres hex minúsculos: $hex" }
    }

    fun toByteArray(): ByteArray = hex.hexToByteArray()

    private companion object {
        val HEX = Regex("[0-9a-f]{64}")
    }
}
