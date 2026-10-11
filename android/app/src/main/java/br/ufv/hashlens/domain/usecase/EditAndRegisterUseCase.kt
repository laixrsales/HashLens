package br.ufv.hashlens.domain.usecase

import android.net.Uri
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.EditRenderer
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.di.DefaultDispatcher
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.domain.model.EditOperationCodec
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de editar e registrar uma imagem do acervo. */
sealed interface EditResult {
    /** O pai ainda não tem registro confirmado; é preciso aguardar (US3, cenário 3). */
    data class ParentNotConfirmed(val status: RegistrationStatus) : EditResult

    /** O arquivo do pai saiu da galeria ou mudou desde o registro: não é mais a imagem registrada. */
    data object ParentUnavailable : EditResult

    /** A versão editada foi para a galeria; o registro dela segue como o da captura. */
    data class Registration(val result: CaptureResult) : EditResult
}

/**
 * Edição → registro (plan "Edição", US3): aplica as operações sobre os bytes do pai em resolução
 * cheia, grava a versão na galeria, relê e confere o SHA-256, garante o vínculo do aparelho e abre
 * `registerEdit` na carteira, com o pai e o texto das operações na assinatura (signature-payload §1).
 *
 * O pai precisa estar `CONFIRMED` e o arquivo dele na galeria ainda precisa ser o registrado; a
 * versão guarda a original da cadeia, que não muda em edições sucessivas (US3, cenário 4).
 */
class EditAndRegisterUseCase @Inject constructor(
    private val renderer: EditRenderer,
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
     * @param parentLocalId imagem do acervo a editar (captura ou edição anterior).
     * @param operations na ordem em que foram aplicadas no editor.
     * @param walletAddress carteira conectada, responsável pela edição.
     * @param onProgress etapas avisadas enquanto o registro não termina (foto salva, vínculo, carteira).
     * @throws IllegalArgumentException sem operações, com operação inválida ou pai inexistente.
     */
    suspend operator fun invoke(
        parentLocalId: Long,
        operations: List<EditOperation>,
        walletAddress: String,
        onProgress: (RegistrationProgress) -> Unit = {}
    ): EditResult {
        require(operations.isNotEmpty()) { "edição sem operações" }
        val operationsText = EditOperationCodec.encode(operations)
        val parent = requireNotNull(images.getById(parentLocalId)) { "imagem local inexistente: $parentLocalId" }
        val parentRecordId = parent.recordId
        return when {
            parent.status != RegistrationStatus.CONFIRMED || parentRecordId == null ->
                EditResult.ParentNotConfirmed(parent.status)
            else -> readParent(parent)?.let { parentBytes ->
                renderAndRegister(
                    parent,
                    parentRecordId,
                    parentBytes,
                    operations,
                    operationsText,
                    walletAddress,
                    onProgress
                )
            } ?: EditResult.ParentUnavailable
        }
    }

    /** Versão em resolução cheia → galeria → releitura → vínculo e `registerEdit`. */
    private suspend fun renderAndRegister(
        parent: LocalImageEntity,
        parentRecordId: Long,
        parentBytes: ByteArray,
        operations: List<EditOperation>,
        operationsText: String,
        walletAddress: String,
        onProgress: (RegistrationProgress) -> Unit
    ): EditResult {
        val editedAt = ZonedDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())
        val (jpeg, sha256, pHash) = withContext(defaultDispatcher) {
            val bytes = renderer.render(parentBytes, operations, editedAt)
            Triple(bytes, sha256Hasher.hash(bytes.inputStream()), perceptualHasher.hash(bytes))
        }

        val uri = gallery.save(jpeg, FILE_NAME.format(editedAt))
        val reread = gallery.read(uri)
        val image = LocalImageEntity(
            contentUri = uri.toString(),
            sha256Hex = sha256.hex,
            pHashHex = pHash.hex,
            origin = ImageOrigin.EDIT,
            parentRecordId = parentRecordId,
            // A original da cadeia: a do pai, ou o próprio pai quando ele é a captura
            originalRecordId = parent.originalRecordId ?: parentRecordId,
            operations = operationsText,
            walletAddress = walletAddress.lowercase(),
            createdAt = clock.millis()
        )
        val rereadSha256 = withContext(defaultDispatcher) { sha256Hasher.hash(reread.inputStream()) }
        if (rereadSha256 != sha256) return failed(image, FailureReason.HASH_MISMATCH)

        return reader.lookupId(sha256).fold(
            onSuccess = { existingId ->
                if (existingId != null) {
                    EditResult.Registration(CaptureResult.AlreadyRegistered(existingId))
                } else {
                    val localId = images.insert(image)
                    onProgress(RegistrationProgress.Saved(localId))
                    EditResult.Registration(
                        submitLocalImage(
                            localId,
                            walletAddress,
                            onProgress,
                            ensureDeviceBound,
                            registerLocalImage,
                            images
                        )
                    )
                }
            },
            onFailure = { failed(image, FailureReason.NETWORK, it.message) }
        )
    }

    /** Bytes do pai, se ainda forem os registrados; `null` se o arquivo saiu da galeria ou mudou. */
    private suspend fun readParent(parent: LocalImageEntity): ByteArray? {
        val bytes = try {
            gallery.read(Uri.parse(parent.contentUri))
        } catch (_: IOException) {
            return null
        }
        val sha256 = withContext(defaultDispatcher) { sha256Hasher.hash(bytes.inputStream()) }
        return bytes.takeIf { sha256.hex == parent.sha256Hex }
    }

    /** Grava a versão já como `FAILED`: ela está na galeria, mas não foi enviada. */
    private suspend fun failed(image: LocalImageEntity, reason: FailureReason, message: String? = null): EditResult {
        val localId = images.insert(
            image.copy(status = RegistrationStatus.FAILED, failureReason = reason, errorMessage = message)
        )
        return EditResult.Registration(CaptureResult.Failed(localId, reason))
    }

    private companion object {
        /** Nome na galeria; os milissegundos evitam colisão entre edições seguidas. */
        val FILE_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("'HashLens_'yyyyMMdd_HHmmss_SSS'_editada.jpg'")
    }
}
