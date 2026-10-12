package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.domain.model.Sha256
import javax.inject.Inject

/**
 * "Tentar de novo" de um registro que falhou, pelo acervo, pela captura e pela edição (FR-014,
 * research R23, data-model §2.3).
 *
 * Consulta o hash on-chain antes de qualquer transação: já registrado → confirma a linha local sem
 * abrir a carteira (a transação "perdida" foi confirmada depois do prazo); senão refaz o vínculo e
 * reabre a carteira com a mesma assinatura do aparelho. A assinatura cobre o `registrant`, por isso o
 * reenvio exige a carteira do registro: com outra conectada, é bloqueado antes de tudo.
 *
 * Também retoma uma imagem presa em `LOCAL_ONLY` (o app fechou entre salvar e enviar): nada foi
 * enviado, como numa falha.
 */
class RetryRegistrationUseCase @Inject constructor(
    private val reader: RegistryReader,
    private val ensureDeviceBound: EnsureDeviceBoundUseCase,
    private val registerLocalImage: RegisterLocalImageUseCase,
    private val images: LocalImageDao
) {
    /**
     * @param walletAddress carteira conectada; precisa ser a que pediu o registro.
     * @param onProgress etapas avisadas enquanto o reenvio não termina (vínculo, carteira).
     * @throws IllegalArgumentException se a imagem não existir ou não estiver em `FAILED` ou `LOCAL_ONLY`.
     */
    suspend operator fun invoke(
        localId: Long,
        walletAddress: String,
        onProgress: (RegistrationProgress) -> Unit = {}
    ): CaptureResult {
        val image = requireNotNull(images.getById(localId)) { "imagem local inexistente: $localId" }
        require(image.status == RegistrationStatus.FAILED || image.status == RegistrationStatus.LOCAL_ONLY) {
            "só registros que não foram enviados são reenviados"
        }
        if (!image.walletAddress.equals(walletAddress, ignoreCase = true)) {
            return CaptureResult.WrongWallet(localId, image.walletAddress)
        }
        return reader.lookupId(Sha256(image.sha256Hex)).fold(
            onSuccess = { recordId ->
                if (recordId != null) {
                    confirm(image, recordId)
                } else {
                    submitLocalImage(localId, walletAddress, onProgress, ensureDeviceBound, registerLocalImage, images)
                }
            },
            onFailure = {
                images.update(image.copy(failureReason = FailureReason.NETWORK, errorMessage = it.message))
                CaptureResult.Failed(localId, FailureReason.NETWORK)
            }
        )
    }

    /** O hash já está on-chain: a linha local é confirmada com esse id, sem nova transação. */
    private suspend fun confirm(image: LocalImageEntity, recordId: Long): CaptureResult {
        images.update(
            image.copy(
                status = RegistrationStatus.CONFIRMED,
                recordId = recordId,
                // Na captura, a original é ela mesma; a edição já sabe a original da cadeia
                originalRecordId = if (image.origin == ImageOrigin.CAPTURE) recordId else image.originalRecordId,
                failureReason = null,
                errorMessage = null
            )
        )
        return CaptureResult.Confirmed(image.localId, recordId)
    }
}
