package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.di.IoDispatcher
import java.math.BigInteger
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.web3j.abi.EventEncoder

/**
 * Recibo de uma transação do ImageRegistry já minerada.
 *
 * @param recordId do evento `ImageRegistered` (registro de imagem confirmado).
 * @param deviceId do evento `DeviceRegistered` (vínculo de aparelho confirmado).
 */
data class TxReceipt(val success: Boolean, val recordId: Long? = null, val deviceId: Int? = null)

/** Transação enviada que não terminou como esperado. */
sealed class TransactionException(val txHash: String, message: String) : Exception(message) {
    /** Recibo com status 0. */
    class Reverted(txHash: String) : TransactionException(txHash, "transação revertida: $txHash")

    /** Sem recibo dentro do prazo. */
    class TimedOut(txHash: String) : TransactionException(txHash, "transação sem recibo no prazo: $txHash")

    /** Recibo com sucesso, mas sem o evento esperado do contrato. */
    class MissingEvent(txHash: String) : TransactionException(txHash, "recibo sem o evento esperado: $txHash")
}

/**
 * Acompanha transações do ImageRegistry pelo recibo (`eth_getTransactionReceipt`) e lê os eventos
 * `ImageRegistered` e `DeviceRegistered` emitidos pelo contrato configurado.
 *
 * [receipt] consulta uma vez (usado pelo `PendingTxWorker`, que aplica o prazo e a reconciliação de
 * R23); [await] consulta até haver recibo.
 */
class ReceiptTracker @Inject constructor(
    private val config: ChainConfig,
    private val rpc: ChainRpc,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Recibo de [txHash]; `null` enquanto a transação não foi minerada.
     *
     * @throws RegistryException.Network ou [RegistryException.Rpc].
     */
    suspend fun receipt(txHash: String): TxReceipt? = withContext(ioDispatcher) {
        rpc.getTransactionReceipt(txHash)?.let { parse(it, config.registryAddress) }
    }

    /**
     * Consulta o recibo a cada [POLL_INTERVAL] até haver um ou esgotar [timeout]. Falhas de rede
     * no meio do caminho não interrompem: a transação já foi enviada.
     *
     * @throws TransactionException.TimedOut se não houver recibo no prazo.
     * @throws RegistryException.Rpc se o nó recusar a consulta.
     */
    suspend fun await(txHash: String, timeout: Duration = config.pendingTimeout): TxReceipt =
        withTimeoutOrNull(timeout.toMillis()) {
            var receipt: TxReceipt? = null
            while (receipt == null) {
                receipt = try {
                    receipt(txHash)
                } catch (_: RegistryException.Network) {
                    null
                }
                if (receipt == null) delay(POLL_INTERVAL.toMillis())
            }
            receipt
        } ?: throw TransactionException.TimedOut(txHash)

    /**
     * Espera a confirmação de `registerDevice` e devolve o `deviceId` do evento `DeviceRegistered`.
     *
     * @throws TransactionException se reverter, não confirmar no prazo ou não trazer o evento.
     */
    suspend fun awaitDeviceRegistered(txHash: String): Int {
        val receipt = await(txHash)
        if (!receipt.success) throw TransactionException.Reverted(txHash)
        return receipt.deviceId ?: throw TransactionException.MissingEvent(txHash)
    }

    companion object {
        /** Bloco da Sepolia sai a cada ~12 s. */
        val POLL_INTERVAL: Duration = Duration.ofSeconds(4)

        private const val HEX_RADIX = 16

        /** `topic0` dos eventos (`contracts/IImageRegistry.sol`). */
        val IMAGE_REGISTERED_TOPIC: String =
            EventEncoder.buildEventSignature("ImageRegistered(uint256,bytes32,uint256,uint256,uint64,address,uint32)")
        val DEVICE_REGISTERED_TOPIC: String = EventEncoder.buildEventSignature("DeviceRegistered(uint32,address,bytes)")

        /**
         * Recibo bruto → [TxReceipt]. Só considera logs emitidos por [contract]; o id vem do primeiro
         * tópico indexado de cada evento.
         */
        fun parse(raw: RawReceipt, contract: String): TxReceipt {
            val logs = raw.logs.filter { it.address.equals(contract, ignoreCase = true) && it.topics.size >= 2 }
            fun firstIndexed(topic: String): BigInteger? = logs
                .firstOrNull { it.topics[0].equals(topic, ignoreCase = true) }
                ?.let { BigInteger(it.topics[1].removePrefix("0x"), HEX_RADIX) }
            return TxReceipt(
                success = raw.success,
                recordId = firstIndexed(IMAGE_REGISTERED_TOPIC)?.longValueExact(),
                deviceId = firstIndexed(DEVICE_REGISTERED_TOPIC)?.intValueExact()
            )
        }
    }
}
