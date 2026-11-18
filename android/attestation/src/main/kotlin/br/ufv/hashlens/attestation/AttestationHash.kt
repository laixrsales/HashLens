package br.ufv.hashlens.attestation

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import org.bouncycastle.crypto.digests.KeccakDigest

/**
 * `attestationHash = keccak256(abi.encode(bytes[]))`, o valor que o contrato grava no `Device` e
 * que o atestador assina na autorização (`contracts/device-authorization.md` §2, research R10).
 */
object AttestationHash {
    private const val WORD = 32

    fun of(chain: List<ByteArray>): ByteArray = keccak256(abiEncode(chain))

    /** ABI de um único parâmetro `bytes[]`: offset, tamanho, offsets dos elementos e cada `bytes`. */
    internal fun abiEncode(chain: List<ByteArray>): ByteArray {
        val elements = chain.map { encodeBytes(it) }
        val out = ByteArrayOutputStream()
        out.write(word(WORD.toLong()))
        out.write(word(chain.size.toLong()))
        var offset = (chain.size * WORD).toLong()
        for (element in elements) {
            out.write(word(offset))
            offset += element.size
        }
        elements.forEach(out::write)
        return out.toByteArray()
    }

    private fun encodeBytes(value: ByteArray): ByteArray {
        val padded = (value.size + WORD - 1) / WORD * WORD
        return word(value.size.toLong()) + value + ByteArray(padded - value.size)
    }

    private fun word(value: Long): ByteArray {
        val magnitude = BigInteger.valueOf(value).toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(WORD - magnitude.size) + magnitude
    }

    internal fun keccak256(data: ByteArray): ByteArray {
        val digest = KeccakDigest(256)
        digest.update(data, 0, data.size)
        return ByteArray(WORD).also { digest.doFinal(it, 0) }
    }
}
