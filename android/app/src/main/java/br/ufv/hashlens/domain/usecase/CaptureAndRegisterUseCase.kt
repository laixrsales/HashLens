package br.ufv.hashlens.domain.usecase

import android.graphics.Bitmap
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.JpegEncoder
import br.ufv.hashlens.data.chain.InsufficientFundsException
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.TransactionException
import br.ufv.hashlens.data.chain.TxCost
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.di.DefaultDispatcher
import br.ufv.hashlens.domain.model.Sha256
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de capturar e registrar uma foto. */
sealed interface CaptureResult {
    /** Na galeria e com a transação enviada; o `PendingTxWorker` acompanha a confirmação. */
    data class Submitted(val localId: Long, val txHash: String) : CaptureResult

    /** Na galeria, mas não registrada; a linha local ficou em `FAILED([reason])`. */
    data class Failed(val localId: Long, val reason: FailureReason) : CaptureResult

    /**
     * Na galeria, mas o saldo não cobre o gás estimado do vínculo ou do registro (FR-003); a carteira
     * não foi aberta e a linha local ficou em `FAILED(INSUFFICIENT_FUNDS)`.
     */
    data class InsufficientFunds(val localId: Long, val cost: TxCost) : CaptureResult

    /** O arquivo já tem registro on-chain ([recordId]); nenhuma transação foi aberta. */
    data class AlreadyRegistered(val recordId: Long) : CaptureResult

    /** Reenvio: o hash já estava on-chain, então a linha local foi confirmada sem nova transação (R23). */
    data class Confirmed(val localId: Long, val recordId: Long) : CaptureResult

    /**
     * Reenvio bloqueado: a carteira conectada não é a que pediu o registro ([expected]); a assinatura
     * do aparelho cobre o `registrant` e não vale para outra carteira (data-model §2.3).
     */
    data class WrongWallet(val localId: Long, val expected: String) : CaptureResult
}

/**
 * Captura → registro (plan "Captura", research R8, Constituição I): codifica o JPEG final, grava
 * na galeria, relê e confere o SHA-256, garante o vínculo do aparelho e abre a transação na carteira.
 *
 * Os identificadores registrados são os dos bytes relidos da galeria. Se a releitura divergir, nada
 * é assinado (`FAILED(HASH_MISMATCH)`).
 */
class CaptureAndRegisterUseCase @Inject constructor(
    private val encoder: JpegEncoder,
    private val sha256Hasher: Sha256Hasher,
    private val perceptualHasher: PerceptualHasher,
    private val gallery: GalleryRepository,
    private val reader: RegistryReader,
    private val ensureDeviceBound: EnsureDeviceBoundUseCase,
    private val registerLocalImage: RegisterLocalImageUseCase,
    private val images: LocalImageDao,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * @param rotationDegrees rotação informada pela câmera (0, 90, 180 ou 270).
     * @param walletAddress carteira conectada que assina o registro.
     * @param onProgress etapas avisadas enquanto o registro não termina (foto salva, vínculo, carteira).
     */
    suspend operator fun invoke(
        photo: Bitmap,
        rotationDegrees: Int,
        walletAddress: String,
        onProgress: (RegistrationProgress) -> Unit = {}
    ): CaptureResult {
        val capturedAt = ZonedDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())
        val (jpeg, sha256, pHash) = withContext(defaultDispatcher) {
            val bytes = encoder.encode(photo, rotationDegrees, capturedAt)
            Triple(bytes, sha256Hasher.hash(bytes.inputStream()), perceptualHasher.hash(bytes))
        }

        val uri = gallery.save(jpeg, FILE_NAME.format(capturedAt))
        val reread = gallery.read(uri)
        val image = LocalImageEntity(
            contentUri = uri.toString(),
            sha256Hex = sha256.hex,
            pHashHex = pHash.hex,
            origin = ImageOrigin.CAPTURE,
            walletAddress = walletAddress.lowercase(),
            createdAt = clock.millis()
        )
        val rereadSha256 = withContext(defaultDispatcher) { sha256Hasher.hash(reread.inputStream()) }
        if (rereadSha256 != sha256) return failed(image, FailureReason.HASH_MISMATCH)

        return lookup(sha256).fold(
            onSuccess = { existingId ->
                if (existingId != null) {
                    CaptureResult.AlreadyRegistered(existingId)
                } else {
                    val localId = images.insert(image)
                    onProgress(RegistrationProgress.Saved(localId))
                    submit(localId, walletAddress, onProgress)
                }
            },
            onFailure = { failed(image, FailureReason.NETWORK, it.message) }
        )
    }

    /**
     * "Tentar de novo" de um registro que falhou (data-model §2.3, research R23): consulta o hash
     * on-chain antes de qualquer transação. Já registrado → confirma sem abrir a carteira; senão,
     * refaz o vínculo e reabre a carteira com a mesma assinatura do aparelho.
     *
     * @throws IllegalArgumentException se a imagem não existir ou não estiver em `FAILED`.
     */
    suspend fun resubmit(
        localId: Long,
        walletAddress: String,
        onProgress: (RegistrationProgress) -> Unit = {}
    ): CaptureResult {
        val image = requireNotNull(images.getById(localId)) { "imagem local inexistente: $localId" }
        require(image.status == RegistrationStatus.FAILED) { "só registros que falharam são reenviados" }
        if (!image.walletAddress.equals(walletAddress, ignoreCase = true)) {
            return CaptureResult.WrongWallet(localId, image.walletAddress)
        }
        return lookup(Sha256(image.sha256Hex)).fold(
            onSuccess = { recordId ->
                if (recordId != null) {
                    images.update(
                        image.copy(
                            status = RegistrationStatus.CONFIRMED,
                            recordId = recordId,
                            originalRecordId = if (image.origin ==
                                ImageOrigin.CAPTURE
                            ) {
                                recordId
                            } else {
                                image.originalRecordId
                            },
                            failureReason = null,
                            errorMessage = null
                        )
                    )
                    CaptureResult.Confirmed(localId, recordId)
                } else {
                    submit(localId, walletAddress, onProgress)
                }
            },
            onFailure = {
                images.update(image.copy(failureReason = FailureReason.NETWORK, errorMessage = it.message))
                CaptureResult.Failed(localId, FailureReason.NETWORK)
            }
        )
    }

    /** Vínculo do aparelho e transação, com a imagem já no acervo como `LOCAL_ONLY` ou `FAILED`. */
    private suspend fun submit(
        localId: Long,
        walletAddress: String,
        onProgress: (RegistrationProgress) -> Unit
    ): CaptureResult = bindDevice(walletAddress, onProgress).fold(
        onSuccess = { device ->
            when (val result = registerLocalImage(localId, walletAddress, device.deviceId, onProgress)) {
                is SubmissionResult.Submitted -> CaptureResult.Submitted(localId, result.txHash)
                is SubmissionResult.Failed -> CaptureResult.Failed(localId, result.reason)
                is SubmissionResult.InsufficientFunds -> CaptureResult.InsufficientFunds(localId, result.cost)
            }
        },
        onFailure = { cause ->
            // Só a recusa é decisão da pessoa; as demais falhas do vínculo podem ser reenviadas
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
    private suspend fun lookup(sha256: Sha256): Result<Long?> = try {
        Result.success(reader.getIdBySha256(sha256))
    } catch (e: RegistryException) {
        Result.failure(e)
    }

    private suspend fun bindDevice(
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

    /** Grava a imagem já como `FAILED`: ela está na galeria, mas não foi enviada. */
    private suspend fun failed(image: LocalImageEntity, reason: FailureReason, message: String? = null): CaptureResult {
        val localId = images.insert(
            image.copy(status = RegistrationStatus.FAILED, failureReason = reason, errorMessage = message)
        )
        return CaptureResult.Failed(localId, reason)
    }

    private companion object {
        /** Nome na galeria; os milissegundos evitam colisão entre capturas seguidas. */
        val FILE_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("'HashLens_'yyyyMMdd_HHmmss_SSS'.jpg'")
    }
}
