package br.ufv.hashlens.core.crypto

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.EllipticCurve

/**
 * Curva P-256 (secp256r1, FIPS 186-4). Parâmetros fixos no código para não depender dos nomes de
 * curva aceitos por cada provedor do Android.
 */
object P256 {
    const val UNCOMPRESSED_KEY_SIZE = 65

    private const val COORDINATE_SIZE = 32
    private const val UNCOMPRESSED_PREFIX: Byte = 0x04
    private const val HEX = 16

    private val P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", HEX)
    private val A = BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", HEX)
    private val B = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", HEX)
    private val GX = BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", HEX)
    private val GY = BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", HEX)

    /** Ordem n do ponto gerador. */
    val ORDER = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", HEX)

    private val SPEC = ECParameterSpec(EllipticCurve(ECFieldFp(P), A, B), ECPoint(GX, GY), ORDER, 1)

    /**
     * Chave on-chain `0x04 ‖ X ‖ Y` (65 bytes) → [PublicKey].
     *
     * @throws IllegalArgumentException se o formato for outro ou o ponto não estiver na curva.
     */
    fun decodePublicKey(bytes: ByteArray): PublicKey {
        require(bytes.size == UNCOMPRESSED_KEY_SIZE && bytes[0] == UNCOMPRESSED_PREFIX) {
            "chave P-256 deve ter $UNCOMPRESSED_KEY_SIZE bytes e prefixo 0x04"
        }
        val x = BigInteger(1, bytes.copyOfRange(1, 1 + COORDINATE_SIZE))
        val y = BigInteger(1, bytes.copyOfRange(1 + COORDINATE_SIZE, UNCOMPRESSED_KEY_SIZE))
        require(isOnCurve(x, y)) { "ponto fora da curva P-256" }
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), SPEC))
    }

    /** `y² ≡ x³ + a·x + b (mod p)`, com coordenadas em `[0, p)`. */
    private fun isOnCurve(x: BigInteger, y: BigInteger): Boolean {
        if (x >= P || y >= P) return false
        val left = (y * y).mod(P)
        val right = (x.pow(3) + A * x + B).mod(P)
        return left == right
    }
}
