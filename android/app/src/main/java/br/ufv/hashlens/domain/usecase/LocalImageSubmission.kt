package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.data.chain.InsufficientFundsException
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.TransactionException
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.domain.model.Sha256

// Etapas comuns à captura (CaptureAndRegisterUseCase) e à edição (EditAndRegisterUseCase)

/**
 * Vínculo do aparelho e transação, com a imagem já no acervo como `LOCAL_ONLY` ou `FAILED`. Falhas
 * do vínculo ficam gravadas como `FAILED`; só a recusa é decisão da pessoa, as demais podem ser
 * reenviadas.
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
        when (val result = registerLocalImage(localId, walletAddress, device.deviceId, onProgress)) {
            is SubmissionResult.Submitted -> CaptureResult.Submitted(localId, result.txHash)
            is SubmissionResult.Failed -> CaptureResult.Failed(localId, result.reason)
            is SubmissionResult.InsufficientFunds -> CaptureResult.InsufficientFunds(localId, result.cost)
        }
    },
    onFailure = { cause ->
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
}
