package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.core.crypto.SignaturePayload
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.chain.TxCost
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.di.IoDispatcher
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de abrir o registro de uma imagem local na carteira. */
sealed interface SubmissionResult {
    /** Transação enviada; o `PendingTxWorker` acompanha até a confirmação. */
    data class Submitted(val txHash: String) : SubmissionResult

    /** Não enviada; a linha local ficou em `FAILED([reason])`. */
    data class Failed(val reason: FailureReason) : SubmissionResult

    /**
     * Não enviada porque o saldo não cobre o [cost] estimado (FR-003); a linha local ficou em
     * `FAILED(INSUFFICIENT_FUNDS)` e a carteira não foi aberta.
     */
    data class InsufficientFunds(val cost: TxCost) : SubmissionResult
}

/**
 * Registra on-chain uma imagem que já está no acervo local, com o aparelho já vinculado
 * ([EnsureDeviceBoundUseCase]). Usado pela captura, pela edição e pelo reenvio.
 *
 * Monta o payload v1 (signature-payload §1), assina com a chave do aparelho e grava a assinatura
 * antes de abrir a carteira; em reenvios a assinatura é reutilizada se o `deviceId` não mudou (ele
 * faz parte do payload). Antes de abrir a carteira, estima o gás e confere o saldo (FR-003): se a
 * transação reverteria ou o saldo não cobre, vai direto a `FAILED`. Transições: → `AWAITING_WALLET`
 * → `PENDING` (com `txHash` e `submittedAt`) ou `FAILED` (data-model §2.3).
 */
class RegisterLocalImageUseCase @Inject constructor(
    private val images: LocalImageDao,
    private val keyManager: DeviceKeyManager,
    private val wallet: WalletSession,
    private val txBuilder: RegistryTxBuilder,
    private val config: ChainConfig,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * @param walletAddress carteira conectada; deve ser a mesma que solicitou o registro, porque a
     *   assinatura cobre o `registrant`.
     * @param onProgress avisado antes de abrir a carteira, com o custo estimado.
     * @throws IllegalArgumentException se a imagem não existir, já tiver sido enviada ou for de outra
     *   carteira.
     */
    suspend operator fun invoke(
        localId: Long,
        walletAddress: String,
        deviceId: Int,
        onProgress: (RegistrationProgress) -> Unit = {}
    ): SubmissionResult {
        val image = requireNotNull(images.getById(localId)) { "imagem local inexistente: $localId" }
        require(image.walletAddress.equals(walletAddress, ignoreCase = true)) {
            "o registro pertence a outra carteira"
        }
        require(image.status == RegistrationStatus.LOCAL_ONLY || image.status == RegistrationStatus.FAILED) {
            "registro já enviado: ${image.status}"
        }

        val signatureHex = image.signatureHex.takeIf { image.deviceId == deviceId }
            ?: sign(image, walletAddress, deviceId).toHexString()
        val signed = image.copy(
            deviceId = deviceId,
            signatureHex = signatureHex,
            txHash = null,
            submittedAt = null,
            failureReason = null,
            errorMessage = null
        )
        val calldata = calldata(signed)
        val cost = when (val check = preflight(signed, walletAddress, calldata)) {
            is Preflight.Blocked -> return check.result
            is Preflight.Clear -> check.cost
        }

        onProgress(RegistrationProgress.AwaitingWallet(cost))
        val awaiting = signed.copy(status = RegistrationStatus.AWAITING_WALLET)
        images.update(awaiting)
        return try {
            val txHash = wallet.sendTransaction(config.registryAddress, calldata)
            images.update(
                awaiting.copy(status = RegistrationStatus.PENDING, txHash = txHash, submittedAt = clock.millis())
            )
            SubmissionResult.Submitted(txHash)
        } catch (e: WalletException) {
            // Só a recusa é decisão da pessoa; as demais falhas da carteira podem ser reenviadas
            val reason = if (e is WalletException.Rejected) FailureReason.REJECTED else FailureReason.NETWORK
            images.update(
                awaiting.copy(status = RegistrationStatus.FAILED, failureReason = reason, errorMessage = e.message)
            )
            SubmissionResult.Failed(reason)
        }
    }

    /** Resultado da estimativa: segue com o [Clear.cost] ou para com o [Blocked.result] já gravado. */
    private sealed interface Preflight {
        data class Clear(val cost: TxCost) : Preflight

        data class Blocked(val result: SubmissionResult) : Preflight
    }

    /** Estima o gás antes de abrir a carteira; as falhas ficam gravadas como `FAILED`, com a assinatura. */
    private suspend fun preflight(image: LocalImageEntity, walletAddress: String, calldata: String): Preflight {
        val cost = try {
            txBuilder.estimate(walletAddress, calldata)
        } catch (e: RegistryException) {
            // O nó respondeu com erro: a transação reverteria (ex.: AlreadyRegistered)
            val reason = if (e is RegistryException.Rpc) FailureReason.REVERTED else FailureReason.NETWORK
            images.update(image.failed(reason, e.message))
            return Preflight.Blocked(SubmissionResult.Failed(reason))
        }
        return if (cost.hasEnoughBalance) {
            Preflight.Clear(cost)
        } else {
            images.update(image.failed(FailureReason.INSUFFICIENT_FUNDS))
            Preflight.Blocked(SubmissionResult.InsufficientFunds(cost))
        }
    }

    private fun LocalImageEntity.failed(reason: FailureReason, message: String? = null) =
        copy(status = RegistrationStatus.FAILED, failureReason = reason, errorMessage = message)

    private suspend fun sign(image: LocalImageEntity, walletAddress: String, deviceId: Int): ByteArray {
        val payload = SignaturePayload(
            chainId = config.chainId,
            contract = config.registryAddress,
            registrant = walletAddress,
            deviceId = deviceId,
            sha256 = Sha256(image.sha256Hex),
            pHash = PHash.fromHex(image.pHashHex),
            parentId = image.parentRecordId ?: 0,
            operations = image.operations.orEmpty()
        )
        return withContext(ioDispatcher) { keyManager.sign(walletAddress, payload.toByteArray()) }
    }

    private fun calldata(image: LocalImageEntity): String {
        val sha256 = Sha256(image.sha256Hex)
        val pHash = PHash.fromHex(image.pHashHex)
        val deviceId = requireNotNull(image.deviceId)
        val signature = requireNotNull(image.signatureHex).hexToByteArray()
        val parentId = image.parentRecordId
        return if (parentId == null) {
            RegistryTxBuilder.registerCapture(sha256, pHash, deviceId, signature)
        } else {
            RegistryTxBuilder.registerEdit(parentId, sha256, pHash, deviceId, signature, image.operations.orEmpty())
        }
    }
}
