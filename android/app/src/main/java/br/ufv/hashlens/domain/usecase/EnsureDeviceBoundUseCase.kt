package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.data.chain.InsufficientFundsException
import br.ufv.hashlens.data.chain.ReceiptTracker
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.chain.TransactionException
import br.ufv.hashlens.data.local.DeviceBindingDao
import br.ufv.hashlens.data.local.DeviceBindingEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aparelho vinculado à carteira, pronto para assinar registros.
 *
 * @param newlyBound `true` se este uso precisou de `registerDevice` (a UI mostra "Vinculando
 *   este aparelho…").
 */
data class BoundDevice(val deviceId: Int, val newlyBound: Boolean)

/**
 * Garante que a chave deste aparelho para [invoke]`(wallet)` está vinculada on-chain (plan "Vínculo
 * do aparelho", research R4 e R25). Roda antes de cada registro.
 *
 * Alias da carteira no Keystore (gera a chave se faltar) → procura a chave pública em
 * `getDevicesOf(carteira)` → se ausente, confere o saldo (FR-003), envia `registerDevice` pela
 * carteira e espera o evento `DeviceRegistered`. Vínculos nunca são revogados: com chave nova, o
 * aparelho antigo segue on-chain e os registros dele continuam válidos.
 *
 * A busca é sempre on-chain, não no vínculo local: este guarda só a carteira, e um novo deploy do
 * contrato invalidaria qualquer atalho.
 */
class EnsureDeviceBoundUseCase @Inject constructor(
    private val keyManager: DeviceKeyManager,
    private val reader: RegistryReader,
    private val wallet: WalletSession,
    private val receipts: ReceiptTracker,
    private val txBuilder: RegistryTxBuilder,
    private val bindings: DeviceBindingDao,
    private val config: ChainConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * @throws InsufficientFundsException se o saldo não cobrir `registerDevice`; a carteira não é aberta.
     * @throws WalletException se a pessoa recusar `registerDevice` ou a carteira falhar.
     * @throws TransactionException se `registerDevice` reverter ou não confirmar no prazo.
     * @throws br.ufv.hashlens.data.chain.RegistryException em falha de leitura do contrato.
     */
    suspend operator fun invoke(walletAddress: String): BoundDevice {
        val publicKey = withContext(ioDispatcher) { keyManager.getOrCreatePublicKey(walletAddress) }
        val binding = DeviceBindingEntity(
            walletAddress = walletAddress.lowercase(),
            publicKeyHex = publicKey.toHexString(),
            status = RegistrationStatus.LOCAL_ONLY
        )

        findOnChain(walletAddress, publicKey)?.let { deviceId ->
            bindings.upsert(binding.copy(deviceId = deviceId, status = RegistrationStatus.CONFIRMED))
            return BoundDevice(deviceId, newlyBound = false)
        }

        val calldata = RegistryTxBuilder.registerDevice(publicKey)
        val cost = txBuilder.estimate(walletAddress, calldata)
        if (!cost.hasEnoughBalance) {
            bindings.upsert(binding.copy(status = RegistrationStatus.FAILED))
            throw InsufficientFundsException(cost)
        }
        return BoundDevice(register(binding, calldata), newlyBound = true)
    }

    /** Envia `registerDevice` pela carteira e espera o `deviceId` do evento, gravando cada passo. */
    private suspend fun register(binding: DeviceBindingEntity, calldata: String): Int {
        bindings.upsert(binding.copy(status = RegistrationStatus.AWAITING_WALLET))
        var txHash: String? = null
        try {
            txHash = wallet.sendTransaction(config.registryAddress, calldata)
            bindings.upsert(binding.copy(txHash = txHash, status = RegistrationStatus.PENDING))
            val deviceId = receipts.awaitDeviceRegistered(txHash)
            bindings.upsert(binding.copy(deviceId = deviceId, txHash = txHash, status = RegistrationStatus.CONFIRMED))
            return deviceId
        } catch (e: WalletException) {
            bindings.upsert(binding.copy(txHash = txHash, status = RegistrationStatus.FAILED))
            throw e
        } catch (e: TransactionException) {
            bindings.upsert(binding.copy(txHash = txHash, status = RegistrationStatus.FAILED))
            throw e
        }
    }

    /** `deviceId` on-chain da carteira com esta chave pública, ou `null`. */
    private suspend fun findOnChain(walletAddress: String, publicKey: ByteArray): Int? =
        // Os mais recentes primeiro: a chave atual costuma ser a do último vínculo
        reader.getDevicesOf(walletAddress).asReversed().firstOrNull { id ->
            reader.getDevice(id)?.publicKey?.contentEquals(publicKey) == true
        }
}
