package br.ufv.hashlens.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Imagem do acervo local: capturada ou editada neste aparelho (data-model §2.1).
 *
 * Arquivos importados de terceiros para edição não viram linha aqui; só a versão editada
 * resultante (`origin = EDIT`).
 */
@Entity(tableName = "local_image", indices = [Index(value = ["sha256Hex"], unique = true)])
data class LocalImageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    /** URI do arquivo no MediaStore. */
    val contentUri: String,
    /** SHA-256 dos bytes relidos da galeria: 64 hex minúsculos. */
    val sha256Hex: String,
    /** pHash v1: 16 hex minúsculos. */
    val pHashHex: String,
    val origin: ImageOrigin,
    /** `recordId` do pai (edições). */
    val parentRecordId: Long? = null,
    /** Raiz da genealogia; preenchido após a confirmação. */
    val originalRecordId: Long? = null,
    /** Operações aplicadas, no texto on-chain (edições). */
    val operations: String? = null,
    /** Carteira que solicitou o registro, em minúsculas; o reenvio exige a mesma. */
    val walletAddress: String,
    val deviceId: Int? = null,
    /** Assinatura do aparelho `r ‖ s` (128 hex), gerada uma vez e reutilizada em reenvios. */
    val signatureHex: String? = null,
    val status: RegistrationStatus = RegistrationStatus.LOCAL_ONLY,
    val txHash: String? = null,
    /** Lido do evento `ImageRegistered` ou de `getIdBySha256` na reconciliação. */
    val recordId: Long? = null,
    /** Epoch ms em que o `txHash` foi recebido; base do prazo de confirmação (R23). */
    val submittedAt: Long? = null,
    val failureReason: FailureReason? = null,
    val errorMessage: String? = null,
    /** Epoch ms. */
    val createdAt: Long,
    /** Falso se o arquivo foi removido da galeria. */
    val fileAvailable: Boolean = true
)
