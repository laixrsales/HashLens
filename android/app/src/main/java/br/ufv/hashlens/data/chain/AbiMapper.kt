package br.ufv.hashlens.data.chain

import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import java.math.BigInteger
import java.time.Instant
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint64
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric

/**
 * Codificação das chamadas `view` do ImageRegistry v2 e decodificação das respostas ABI e do evento
 * `DeviceAttested` (`contracts/IImageRegistry.sol`). Endereços saem no formato EIP-55; ids 0
 * ("inexistente") viram `null`.
 */
// Uma função de chamada e uma de resposta por função `view` do contrato
@Suppress("TooManyFunctions")
object AbiMapper {
    // ─────────────── Chamadas (calldata de eth_call) ───────────────

    fun getRecord(id: Long): String = encode("getRecord", Uint256(id))

    fun getIdBySha256(sha256: Sha256): String = encode("getIdBySha256", Bytes32(sha256.toByteArray()))

    fun getIdsByPHash(pHash: PHash): String = encode("getIdsByPHash", Uint64(pHash.toBigInteger()))

    fun getChildren(id: Long): String = encode("getChildren", Uint256(id))

    fun getDevice(id: Int): String = encode("getDevice", Uint32(id.toLong()))

    fun getDevicesOf(owner: String): String = encode("getDevicesOf", Address(owner))

    fun totalRecords(): String = encode("totalRecords")

    // ─────────────── Respostas ───────────────

    /** Resposta de `getRecord`; `null` quando o registro não existe (id 0). */
    fun decodeRecord(response: String): ImageRecord? {
        val struct = decodeSingle(response, object : TypeReference<RecordStruct>() {})
        val id = struct.id.value.toLongExact()
        if (id == 0L) return null
        val parentId = struct.parentId.value.toLongExact()
        return ImageRecord(
            id = id,
            sha256 = Sha256(struct.sha256Hash.value.toHexString()),
            pHash = PHash(struct.pHash.value.toLong().toULong()),
            parentId = parentId.takeIf { it != 0L },
            originalId = struct.originalId.value.toLongExact(),
            registrant = Keys.toChecksumAddress(struct.registrant.value),
            deviceId = struct.deviceId.value.toIntExact(),
            signature = struct.sigR.value + struct.sigS.value,
            timestamp = Instant.ofEpochSecond(struct.timestamp.value.toLongExact()),
            operations = struct.operations.value
        )
    }

    /** Resposta de `getDevice`; `null` quando o aparelho não existe (id 0). */
    fun decodeDevice(response: String): Device? {
        val struct = decodeSingle(response, object : TypeReference<DeviceStruct>() {})
        val id = struct.id.value.toIntExact()
        if (id == 0) return null
        return Device(
            id = id,
            owner = Keys.toChecksumAddress(struct.owner.value),
            publicKey = struct.publicKey.value,
            registeredAt = Instant.ofEpochSecond(struct.registeredAt.value.toLongExact()),
            attestationHash = struct.attestationHash.value,
            registeredBlock = struct.registeredBlock.value.toLongExact()
        )
    }

    // ─────────────── Evento DeviceAttested ───────────────

    /** `topics[0]` de `DeviceAttested(uint32 indexed deviceId, bytes[] attestationChain)`. */
    val DEVICE_ATTESTED_TOPIC: String = EventEncoder.encode(
        Event(
            "DeviceAttested",
            listOf(object : TypeReference<Uint32>(true) {}, object : TypeReference<DynamicArray<DynamicBytes>>() {})
        )
    )

    /** `topics[1]` de um evento com `uint32 indexed deviceId`: o id em 32 bytes. */
    fun deviceIdTopic(deviceId: Int): String =
        Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(deviceId.toLong()), TOPIC_HEX_LENGTH)

    /**
     * Cadeia de atestação publicada no vínculo (DER, da folha para a raiz); `null` se [log] não for
     * o `DeviceAttested` do aparelho [deviceId]. Dados que não decodificam viram
     * [IllegalArgumentException].
     */
    fun decodeAttestationChain(log: RawLog, deviceId: Int): List<ByteArray>? {
        val isEvent = log.topics.getOrNull(0).equals(DEVICE_ATTESTED_TOPIC, ignoreCase = true) &&
            log.topics.getOrNull(1).equals(deviceIdTopic(deviceId), ignoreCase = true)
        if (!isEvent) return null
        return decodeSingle(log.data, object : TypeReference<DynamicArray<DynamicBytes>>() {}).value.map { it.value }
    }

    /** Resposta de `getDevicesOf` (`uint32[]`). */
    fun decodeDeviceIds(response: String): List<Int> =
        decodeSingle(response, object : TypeReference<DynamicArray<Uint32>>() {}).value.map { it.value.toIntExact() }

    /** Resposta de `getIdsByPHash` e `getChildren` (`uint256[]`). */
    fun decodeIds(response: String): List<Long> =
        decodeSingle(response, object : TypeReference<DynamicArray<Uint256>>() {}).value.map { it.value.toLongExact() }

    /** Resposta de `getIdBySha256` e `totalRecords` (`uint256`; 0 = não encontrado). */
    fun decodeId(response: String): Long =
        decodeSingle(response, object : TypeReference<Uint256>() {}).value.toLongExact()

    // ─────────────── Apoio ───────────────

    /** Um tópico é uma palavra de 32 bytes: 64 dígitos hex. */
    private const val TOPIC_HEX_LENGTH = 64

    private val RESPONSE = Regex("0x([0-9a-fA-F]{64})+")

    private fun encode(name: String, vararg inputs: Type<*>): String =
        FunctionEncoder.encode(Function(name, inputs.toList(), emptyList()))

    /** Decodifica um único valor de retorno; qualquer problema vira [IllegalArgumentException]. */
    private fun <T : Type<*>> decodeSingle(response: String, type: TypeReference<T>): T {
        require(RESPONSE.matches(response)) { "resposta ABI malformada" }
        @Suppress("UNCHECKED_CAST")
        val values = try {
            FunctionReturnDecoder.decode(response, listOf(type as TypeReference<Type<*>>))
        } catch (
            // O web3j lança vários tipos de RuntimeException para ABI malformado (índice, cast, número)
            @Suppress("TooGenericExceptionCaught") e: RuntimeException
        ) {
            throw IllegalArgumentException("resposta ABI inválida", e)
        }
        require(values.size == 1) { "resposta ABI com ${values.size} valores" }
        @Suppress("UNCHECKED_CAST")
        return values.single() as T
    }

    private fun BigInteger.toLongExact(): Long = try {
        longValueExact()
    } catch (e: ArithmeticException) {
        throw IllegalArgumentException("valor não cabe em Long: $this", e)
    }

    private fun BigInteger.toIntExact(): Int = try {
        intValueExact()
    } catch (e: ArithmeticException) {
        throw IllegalArgumentException("valor não cabe em Int: $this", e)
    }

    private fun PHash.toBigInteger(): BigInteger = BigInteger(value.toString())

    /** `ImageRecord` do contrato, na ordem dos campos (web3j decodifica pelo construtor). */
    class RecordStruct(
        val id: Uint256,
        val sha256Hash: Bytes32,
        val pHash: Uint64,
        val parentId: Uint256,
        val originalId: Uint256,
        val registrant: Address,
        val deviceId: Uint32,
        val sigR: Bytes32,
        val sigS: Bytes32,
        val timestamp: Uint64,
        val operations: Utf8String
    ) : DynamicStruct(
        id, sha256Hash, pHash, parentId, originalId, registrant, deviceId, sigR, sigS, timestamp, operations
    )

    /** `Device` do contrato v2, na ordem dos campos. */
    class DeviceStruct(
        val id: Uint32,
        val owner: Address,
        val publicKey: DynamicBytes,
        val registeredAt: Uint64,
        val attestationHash: Bytes32,
        val registeredBlock: Uint64
    ) : DynamicStruct(id, owner, publicKey, registeredAt, attestationHash, registeredBlock)
}
