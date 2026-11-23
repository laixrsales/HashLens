package br.ufv.hashlens.attester.audit

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.time.Instant
import org.web3j.crypto.Hash

/**
 * O pouco de ABI que a auditoria usa, escrito à parte do app para a conferência ser independente:
 * a chamada `getDevice(uint32)`, a resposta `Device` e o evento `DeviceAttested(uint32 indexed,
 * bytes[])` (`contracts/src/IImageRegistry.sol`).
 */
object Abi {
    private const val WORD = 32
    private const val HEX_RADIX = 16
    private const val SELECTOR_BYTES = 4
    private const val ADDRESS_BYTES = 20

    /** Campos da cabeça da tupla `Device`: id, owner, offset de publicKey, registeredAt, hash, bloco. */
    private const val DEVICE_HEAD_WORDS = 6
    private const val FIELD_PUBLIC_KEY = 2
    private const val FIELD_REGISTERED_AT = 3
    private const val FIELD_HASH = 4
    private const val FIELD_BLOCK = 5

    val DEVICE_ATTESTED_TOPIC: String = hex(Hash.sha3("DeviceAttested(uint32,bytes[])".toByteArray()))

    fun getDeviceCall(deviceId: Int): String =
        hex(Hash.sha3("getDevice(uint32)".toByteArray()).copyOf(SELECTOR_BYTES)) +
            word(BigInteger.valueOf(deviceId.toLong()))
                .removePrefix("0x")

    /** Palavra de 32 bytes em hex com `0x` (tópico ou argumento). */
    fun word(value: BigInteger): String = hex(wordBytes(value))

    /** Resposta de `getDevice`; `null` se o id é 0 (aparelho inexistente). */
    fun decodeDevice(response: String): AuditedDevice? {
        val data = bytes(response)
        val tuple = uint(data, 0).toInt()
        require(data.size >= tuple + DEVICE_HEAD_WORDS * WORD) { "resposta curta de getDevice" }
        val id = uint(data, tuple).intValueExact()
        if (id == 0) return null
        fun field(index: Int) = tuple + index * WORD
        val owner = data.copyOfRange(field(FIELD_PUBLIC_KEY) - ADDRESS_BYTES, field(FIELD_PUBLIC_KEY))
        val publicKey = dynamicBytes(data, tuple + uint(data, field(FIELD_PUBLIC_KEY)).toInt())
        return AuditedDevice(
            id = id,
            owner = hex(owner),
            publicKey = publicKey,
            registeredAt = Instant.ofEpochSecond(uint(data, field(FIELD_REGISTERED_AT)).longValueExact()),
            attestationHash = data.copyOfRange(field(FIELD_HASH), field(FIELD_BLOCK)),
            registeredBlock = uint(data, field(FIELD_BLOCK)).longValueExact()
        )
    }

    /** `data` do evento `DeviceAttested`: o `bytes[]` da cadeia, da folha para a raiz. */
    fun decodeChain(data: String): List<ByteArray>? = runCatching {
        val raw = bytes(data)
        val array = uint(raw, 0).toInt()
        val count = uint(raw, array).toInt()
        val elements = array + WORD
        List(count) { i -> dynamicBytes(raw, elements + uint(raw, elements + i * WORD).toInt()) }
    }.getOrNull()

    /** `abi.encode(bytes[])`, o que o contrato e o atestador hasheiam (device-authorization.md §2). */
    fun encodeBytesArray(chain: List<ByteArray>): ByteArray {
        val elements = chain.map { wordBytes(BigInteger.valueOf(it.size.toLong())) + padded(it) }
        val out = ByteArrayOutputStream()
        out.write(wordBytes(BigInteger.valueOf(WORD.toLong())))
        out.write(wordBytes(BigInteger.valueOf(chain.size.toLong())))
        var offset = chain.size.toLong() * WORD
        for (element in elements) {
            out.write(wordBytes(BigInteger.valueOf(offset)))
            offset += element.size
        }
        elements.forEach(out::write)
        return out.toByteArray()
    }

    fun hex(bytes: ByteArray): String = "0x" + bytes.joinToString("") { "%02x".format(it) }

    fun bytes(hex: String): ByteArray {
        val digits = hex.removePrefix("0x")
        require(digits.length % 2 == 0) { "hex de tamanho ímpar" }
        return ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(HEX_RADIX).toByte() }
    }

    private fun uint(data: ByteArray, at: Int): BigInteger = BigInteger(1, data.copyOfRange(at, at + WORD))

    private fun dynamicBytes(data: ByteArray, at: Int): ByteArray {
        val length = uint(data, at).toInt()
        return data.copyOfRange(at + WORD, at + WORD + length)
    }

    private fun wordBytes(value: BigInteger): ByteArray {
        val magnitude = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(WORD - magnitude.size) + magnitude
    }

    private fun padded(value: ByteArray): ByteArray = value + ByteArray((WORD - value.size % WORD) % WORD)
}
