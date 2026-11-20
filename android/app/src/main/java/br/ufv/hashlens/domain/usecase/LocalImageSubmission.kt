package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.data.chain.InsufficientFundsException
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.TransactionException
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.domain.model.AttesterUnavailableException
import br.ufv.hashlens.domain.model.DeviceNotEligibleException
import br.ufv.hashlens.domain.model.Sha256

// Etapas comuns à captura (CaptureAndRegisterUseCase) e à edição (EditAndRegisterUseCase)

/**
 * Vínculo do aparelho e transação, com a imagem já no acervo como `LOCAL_ONLY` ou `FAILED`. Falhas
 * do vínculo ficam gravadas como `FAILED`; só a recusa é decisão da pessoa, as demais podem ser
 * reenviadas. Recusa ou falha do atestador (020 US2) deixam a imagem `LOCAL_ONLY`, "não registrada":
 * a carteira nem chegou a abrir, e o reenvio não pede nova captura.
 */
internal suspend fun submitLocalImage(
    localId: Long,
    walletAddress: String,
    onProgress: (RegistrationProgress) -> Unit,
    ensureDeviceBound: EnsureDeviceBoundUseCase,
    registerLocalImage: RegisterLocalImageUseCase,
    images: LocalImageDao
): CaptureResult = bindDevice(ensureDeviceBound, walletAddress, onProgress).fold(
    onSuccess = { device ->
        // A carteira do vínculo: pode ser outra se a pessoa trocou de conta durante a verificação
        when (val result = registerLocalImage(localId, device.walletAddress, device.deviceId, onProgress)) {
            is SubmissionResult.Submitted -> CaptureResult.Submitted(localId, result.txHash)
            is SubmissionResult.Failed -> CaptureResult.Failed(localId, result.reason)
            is SubmissionResult.InsufficientFunds -> CaptureResult.InsufficientFunds(localId, result.cost)
        }
    },
    onFailure = { cause ->
        if (cause is DeviceNotEligibleException || cause is AttesterUnavailableException) {
            return@fold notBound(localId, cause, images)
        }
        val reason = when (cause) {
            is InsufficientFundsException -> FailureReason.INSUFFICIENT_FUNDS
            is WalletException.Rejected -> FailureReason.REJECTED
            else -> FailureReason.NETWORK
        }
        val image = requireNotNull(images.getById(localId))
        images.update(
            image.copy(status = RegistrationStatus.FAILED, failureReason = reason, errorMessage = cause.message)
        )
        if (cause is InsufficientFundsException) {
            CaptureResult.InsufficientFunds(localId, cause.cost)
        } else {
            CaptureResult.Failed(localId, reason)
        }
    }
)

/** O atestador recusou ou não respondeu: a imagem volta a `LOCAL_ONLY`, sem motivo de falha. */
private suspend fun notBound(localId: Long, cause: Throwable, images: LocalImageDao): CaptureResult {
    val image = requireNotNull(images.getById(localId))
    images.update(
        image.copy(status = RegistrationStatus.LOCAL_ONLY, failureReason = null, errorMessage = cause.message)
    )
    return if (cause is DeviceNotEligibleException) {
        CaptureResult.NotEligible(localId, cause.reason)
    } else {
        CaptureResult.AttesterUnavailable(localId)
    }
}

/** Id on-chain deste SHA-256; falha de leitura do contrato vira [Result.failure]. */
internal suspend fun RegistryReader.lookupId(sha256: Sha256): Result<Long?> = try {
    Result.success(getIdBySha256(sha256))
} catch (e: RegistryException) {
    Result.failure(e)
}

private suspend fun bindDevice(
    ensureDeviceBound: EnsureDeviceBoundUseCase,
    walletAddress: String,
    onProgress: (RegistrationProgress) -> Unit
): Result<BoundDevice> = try {
    Result.success(ensureDeviceBound(walletAddress, onProgress))
} catch (e: InsufficientFundsException) {
    Result.failure(e)
} catch (e: WalletException) {
    Result.failure(e)
} catch (e: TransactionException) {
    Result.failure(e)
} catch (e: RegistryException) {
    Result.failure(e)
} catch (e: DeviceNotEligibleException) {
    // A carteira não chegou a abrir (020 FR-010)
    Result.failure(e)
} catch (e: AttesterUnavailableException) {
    Result.failure(e)
}
