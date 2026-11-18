package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Leitura do ImageRegistry pelas funções `view` e pelo evento `DeviceAttested` (sem carteira e
 * sem gás).
 *
 * Toda falha sai como [RegistryException]: rede ([RegistryException.Network]), erro do nó
 * ([RegistryException.Rpc]) ou retorno que não decodifica ([RegistryException.InvalidResponse]).
 */
class RegistryReader(
    private val config: ChainConfig,
    private val ethCall: EthCall,
    private val rpc: ChainRpc,
    private val ioDispatcher: CoroutineDispatcher
) {
    /** Registro pelo id; `null` se não existir. */
    suspend fun getRecord(id: Long): ImageRecord? = call(AbiMapper.getRecord(id), AbiMapper::decodeRecord)

    /** Id do registro com este SHA-256; `null` se não houver. */
    suspend fun getIdBySha256(sha256: Sha256): Long? =
        call(AbiMapper.getIdBySha256(sha256), AbiMapper::decodeId).takeIf { it != 0L }

    /** Ids de todos os registros com exatamente este pHash (pode ser vazio). */
    suspend fun getIdsByPHash(pHash: PHash): List<Long> = call(AbiMapper.getIdsByPHash(pHash), AbiMapper::decodeIds)

    /** Ids das versões editadas diretamente a partir do registro [id]. */
    suspend fun getChildren(id: Long): List<Long> = call(AbiMapper.getChildren(id), AbiMapper::decodeIds)

    /** Aparelho pelo id; `null` se não existir. */
    suspend fun getDevice(id: Int): Device? = call(AbiMapper.getDevice(id), AbiMapper::decodeDevice)

    /** Ids dos aparelhos vinculados à carteira [owner]. */
    suspend fun getDevicesOf(owner: String): List<Int> = call(AbiMapper.getDevicesOf(owner), AbiMapper::decodeDeviceIds)

    suspend fun totalRecords(): Long = call(AbiMapper.totalRecords(), AbiMapper::decodeId)

    /**
     * Cadeia de atestação publicada no vínculo do aparelho [deviceId], lida do evento
     * `DeviceAttested` do bloco [block] (`Device.registeredBlock`). Buscar um único bloco cabe no
     * limite de intervalo dos nós públicos (020 research R10). `null` se o evento não estiver lá.
     */
    suspend fun getAttestationChain(deviceId: Int, block: Long): List<ByteArray>? = withContext(ioDispatcher) {
        val topics = listOf(AbiMapper.DEVICE_ATTESTED_TOPIC, AbiMapper.deviceIdTopic(deviceId))
        val logs = rpc.getLogs(config.registryAddress, topics, fromBlock = block, toBlock = block)
        try {
            logs.firstNotNullOfOrNull { AbiMapper.decodeAttestationChain(it, deviceId) }
        } catch (e: IllegalArgumentException) {
            throw RegistryException.InvalidResponse(e)
        }
    }

    private suspend fun <T> call(data: String, decode: (String) -> T): T = withContext(ioDispatcher) {
        val response = ethCall.call(config.registryAddress, data)
        try {
            decode(response)
        } catch (e: IllegalArgumentException) {
            throw RegistryException.InvalidResponse(e)
        }
    }
}
