package br.ufv.hashlens.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Vínculo deste aparelho com uma carteira (data-model §2.2): a chave do alias da carteira e o
 * `deviceId` on-chain.
 *
 * Se a chave do alias mudar (perda, research R25), a linha é substituída; o aparelho antigo segue
 * on-chain e os registros feitos com ele continuam válidos.
 */
@Entity(tableName = "device_binding")
data class DeviceBindingEntity(
    /** Em minúsculas; também define o alias do Keystore (research R4). */
    @PrimaryKey val walletAddress: String,
    /** Chave pública `0x04 ‖ X ‖ Y` em hex (130 caracteres). */
    val publicKeyHex: String,
    /** Nulo até a confirmação de `registerDevice`. */
    val deviceId: Int? = null,
    val txHash: String? = null,
    val status: RegistrationStatus
)
