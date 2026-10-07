package br.ufv.hashlens.core.crypto

import java.math.BigInteger

/**
 * Conversão da assinatura ECDSA P-256 entre DER (Android Keystore / `java.security`) e o formato
 * bruto `r ‖ s` de 64 bytes gravado on-chain (signature-payload §2 e §3).
 */
object SignatureCodec {
    const val RAW_SIZE = 64

    private const val COMPONENT_SIZE = RAW_SIZE / 2
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_INTEGER = 0x02
    private const val MAX_SHORT_LENGTH = 0x7f
    private const val BYTE_MASK = 0xff
    private const val SIGN_BIT = 0x80

    /** Ordem n da curva P-256. */
    private val ORDER = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
    private val HALF_ORDER = ORDER.shiftRight(1)

    /**
     * DER → `r ‖ s`, já com normalização low-S (`s > n/2` vira `n − s`), para que cada mensagem
     * tenha uma única representação registrada.
     *
     * @throws IllegalArgumentException se [der] não for uma assinatura DER válida.
     */
    fun derToRaw(der: ByteArray): ByteArray {
        val reader = DerReader(der)
        reader.expectTag(TAG_SEQUENCE)
        require(reader.readLength() == reader.remaining) { "comprimento da SEQUENCE não confere" }
        val r = reader.readInteger()
        var s = reader.readInteger()
        require(reader.remaining == 0) { "bytes sobrando após a assinatura" }
        if (s > HALF_ORDER) s = ORDER - s
        return toFixed(r) + toFixed(s)
    }

    /** `r ‖ s` → DER canônico (INTEGERs mínimos), para verificar com `SHA256withECDSA`. */
    fun rawToDer(raw: ByteArray): ByteArray {
        require(raw.size == RAW_SIZE) { "assinatura bruta deve ter $RAW_SIZE bytes: ${raw.size}" }
        val r = encodeInteger(BigInteger(1, raw.copyOfRange(0, COMPONENT_SIZE)))
        val s = encodeInteger(BigInteger(1, raw.copyOfRange(COMPONENT_SIZE, RAW_SIZE)))
        return byteArrayOf(TAG_SEQUENCE.toByte(), (r.size + s.size).toByte()) + r + s
    }

    /** `BigInteger.toByteArray` já é o complemento de dois mínimo exigido pelo DER. */
    private fun encodeInteger(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return byteArrayOf(TAG_INTEGER.toByte(), bytes.size.toByte()) + bytes
    }

    private fun toFixed(value: BigInteger): ByteArray {
        val magnitude = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(COMPONENT_SIZE - magnitude.size) + magnitude
    }

    private class DerReader(private val bytes: ByteArray) {
        private var position = 0
        val remaining: Int get() = bytes.size - position

        fun expectTag(tag: Int) {
            require(next() == tag) { "tag DER inesperada" }
        }

        /** Só a forma curta: uma assinatura P-256 em DER nunca passa de 72 bytes. */
        fun readLength(): Int {
            val length = next()
            require(length <= MAX_SHORT_LENGTH) { "comprimento DER fora da forma curta" }
            return length
        }

        fun readInteger(): BigInteger {
            expectTag(TAG_INTEGER)
            val length = readLength()
            require(length in 1..remaining) { "INTEGER com comprimento inválido" }
            val content = bytes.copyOfRange(position, position + length)
            position += length
            require(content[0].toInt() and SIGN_BIT == 0) { "INTEGER negativo" }
            require(length == 1 || content[0] != 0.toByte() || content[1].toInt() and SIGN_BIT != 0) {
                "INTEGER não mínimo"
            }
            val value = BigInteger(1, content)
            require(value.signum() > 0 && value < ORDER) { "componente fora de 1..n−1" }
            return value
        }

        private fun next(): Int {
            require(position < bytes.size) { "DER truncado" }
            return bytes[position++].toInt() and BYTE_MASK
        }
    }
}
