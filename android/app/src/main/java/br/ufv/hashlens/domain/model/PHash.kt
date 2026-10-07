package br.ufv.hashlens.domain.model

/** Hash perceptual de 64 bits (hashing-spec §2); on-chain como `uint64`. */
@JvmInline
value class PHash(val value: ULong) {
    /** 16 caracteres hex minúsculos, com zeros à esquerda (ex.: `9e4c398ab9d93331`). */
    val hex: String get() = value.toString(radix = 16).padStart(HEX_LENGTH, '0')

    companion object {
        private const val HEX_LENGTH = 16
        private val HEX = Regex("[0-9a-f]{$HEX_LENGTH}")

        fun fromHex(hex: String): PHash {
            require(HEX.matches(hex)) { "pHash deve ter 16 caracteres hex minúsculos: $hex" }
            return PHash(hex.toULong(radix = 16))
        }
    }
}
