package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.P256
import br.ufv.hashlens.core.crypto.SignatureCodec
import br.ufv.hashlens.di.IoDispatcher
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint64

/**
 * Custo estimado de uma transação e se o saldo da carteira cobre.
 *
 * @param gasLimit estimativa do nó com margem de [RegistryTxBuilder.GAS_MARGIN_PERCENT].
 * @param gasPrice preço atual do gás (wei); a carteira define a taxa final.
 */
data class TxCost(val gasLimit: BigInteger, val gasPrice: BigInteger, val balance: BigInteger) {
    /** Custo máximo previsto, em wei. */
    val maxCost: BigInteger get() = gasLimit * gasPrice

    val hasEnoughBalance: Boolean get() = balance >= maxCost
}

/**
 * Transações de escrita do ImageRegistry (`contracts/IImageRegistry.sol`).
 *
 * O calldata é montado aqui (funções do `companion`) e enviado pela carteira
 * (`WalletSession.sendTransaction`); o app nunca assina transações. [estimate] confere antes do
 * envio se a transação passaria e se o saldo cobre o gás.
 */
class RegistryTxBuilder @Inject constructor(
    private val config: ChainConfig,
    private val rpc: ChainRpc,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Estima o gás de enviar [data] ao contrato a partir de [from] e compara com o saldo.
     *
     * @throws RegistryException.Rpc se a transação reverteria (ex.: `AlreadyRegistered`).
     * @throws RegistryException.Network sem conexão com o nó.
     */
    suspend fun estimate(from: String, data: String): TxCost = withContext(ioDispatcher) {
        val estimated = rpc.estimateGas(from, config.registryAddress, data)
        TxCost(
            gasLimit = estimated * BigInteger.valueOf(PERCENT + GAS_MARGIN_PERCENT) / BigInteger.valueOf(PERCENT),
            gasPrice = rpc.gasPrice(),
            balance = rpc.getBalance(from)
        )
    }

    companion object {
        /** Margem sobre a estimativa do nó, como as carteiras costumam aplicar. */
        const val GAS_MARGIN_PERCENT = 20L

        private const val PERCENT = 100L
        private const val UNCOMPRESSED_PREFIX: Byte = 0x04
        private const val MAX_OPERATIONS_BYTES = 256
        private const val COMPONENT_SIZE = SignatureCodec.RAW_SIZE / 2

        /** `registerDevice(bytes publicKey)` com a chave P-256 não comprimida (65 bytes, `0x04 ‖ X ‖ Y`). */
        fun registerDevice(publicKey: ByteArray): String {
            require(publicKey.size == P256.UNCOMPRESSED_KEY_SIZE && publicKey[0] == UNCOMPRESSED_PREFIX) {
                "chave pública deve ter ${P256.UNCOMPRESSED_KEY_SIZE} bytes e prefixo 0x04"
            }
            return encode("registerDevice", DynamicBytes(publicKey))
        }

        /** `registerCapture(sha256Hash, pHash, deviceId, sigR, sigS)`; [signature] em `r ‖ s` (64 bytes). */
        fun registerCapture(sha256: Sha256, pHash: PHash, deviceId: Int, signature: ByteArray): String {
            val (r, s) = split(signature)
            return encode("registerCapture", Bytes32(sha256.toByteArray()), uint64(pHash), uint32(deviceId), r, s)
        }

        /** `registerEdit(parentId, sha256Hash, pHash, deviceId, sigR, sigS, operations)`. */
        fun registerEdit(
            parentId: Long,
            sha256: Sha256,
            pHash: PHash,
            deviceId: Int,
            signature: ByteArray,
            operations: String
        ): String {
            require(parentId > 0) { "edição exige pai: $parentId" }
            require(operations.encodeToByteArray().size in 1..MAX_OPERATIONS_BYTES) {
                "operations deve ter de 1 a $MAX_OPERATIONS_BYTES bytes"
            }
            val (r, s) = split(signature)
            return encode(
                "registerEdit",
                Uint256(parentId),
                Bytes32(sha256.toByteArray()),
                uint64(pHash),
                uint32(deviceId),
                r,
                s,
                Utf8String(operations)
            )
        }

        private fun split(signature: ByteArray): Pair<Bytes32, Bytes32> {
            require(signature.size == SignatureCodec.RAW_SIZE) {
                "assinatura deve ter ${SignatureCodec.RAW_SIZE} bytes (r ‖ s): ${signature.size}"
            }
            return Bytes32(signature.copyOfRange(0, COMPONENT_SIZE)) to
                Bytes32(signature.copyOfRange(COMPONENT_SIZE, SignatureCodec.RAW_SIZE))
        }

        /** `uint64` sem sinal: pHashes acima de `Long.MAX_VALUE` são comuns. */
        private fun uint64(pHash: PHash) = Uint64(BigInteger(pHash.value.toString()))

        private fun uint32(deviceId: Int): Uint32 {
            require(deviceId > 0) { "deviceId inválido: $deviceId" }
            return Uint32(deviceId.toLong())
        }

        private fun encode(name: String, vararg inputs: Type<*>): String =
            FunctionEncoder.encode(Function(name, inputs.toList(), emptyList()))
    }
}
