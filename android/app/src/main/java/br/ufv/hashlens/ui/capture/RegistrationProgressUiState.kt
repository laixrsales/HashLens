package br.ufv.hashlens.ui.capture

import androidx.compose.ui.graphics.ImageBitmap
import br.ufv.hashlens.ui.components.TechnicalItem
import java.math.BigDecimal
import java.time.Instant

/**
 * Estado do Acompanhamento do registro (ux.md §4.5).
 *
 * @property thumbnail miniatura da foto; `null` enquanto carrega ou se o arquivo saiu da galeria.
 * @property technical detalhes técnicos do registro confirmado (registro, identificadores, transação).
 */
data class RegistrationProgressUiState(
    val stage: RegistrationStage,
    val thumbnail: ImageBitmap? = null,
    val technical: List<TechnicalItem> = emptyList()
)

/** Em que ponto está o registro (data-model §2.3, na linguagem da tela). */
sealed interface RegistrationStage {
    /**
     * Vinculando este aparelho à carteira antes do registro (research R4).
     *
     * @param newKey `true` quando a chave do aparelho mudou (reinstalação, research R25).
     */
    data class BindingDevice(val newKey: Boolean = false) : RegistrationStage

    /** Carteira aberta pedindo a aprovação do registro; [estimatedCost] em SepoliaETH, se conhecido. */
    data class AwaitingApproval(val estimatedCost: BigDecimal? = null) : RegistrationStage

    /** Enviado; a rede ainda não confirmou. */
    data object Pending : RegistrationStage

    data class Confirmed(val registeredAt: Instant) : RegistrationStage

    data class Failed(val reason: RegistrationFailure) : RegistrationStage

    /** A carteira não foi aberta: o saldo de teste não cobre o custo estimado (FR-003). */
    data class InsufficientFunds(val cost: BigDecimal, val balance: BigDecimal) : RegistrationStage

    /** O arquivo já tem registro; nenhuma transação foi aberta. */
    data class AlreadyRegistered(val recordId: Long) : RegistrationStage
}

enum class RegistrationFailure {
    /** A pessoa recusou na carteira. */
    Rejected,

    /** Sem conexão com a rede ou com a carteira. */
    Network,

    /** A rede não confirmou dentro do prazo, e o registro não consta on-chain (research R23). */
    Timeout,

    /** A transação reverteu e o hash não está on-chain (recibo com status 0). */
    Reverted,

    /** O arquivo relido da galeria não confere com o capturado; nada foi assinado (research R8). */
    HashMismatch,

    /** A galeria recusou a gravação (ex.: sem espaço); nada foi registrado. */
    SaveFailed
}

sealed interface RegistrationProgressAction {
    data object Close : RegistrationProgressAction

    /** Registro confirmado: volta ao Início. */
    data object Done : RegistrationProgressAction

    data object BackHome : RegistrationProgressAction

    data object Retry : RegistrationProgressAction

    data object RetakePhoto : RegistrationProgressAction

    data object OpenFaucet : RegistrationProgressAction

    data class ViewRecord(val recordId: Long) : RegistrationProgressAction

    /** Link dos detalhes técnicos (explorador da rede). */
    data class OpenUrl(val url: String) : RegistrationProgressAction
}
