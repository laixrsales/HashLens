package br.ufv.hashlens.ui.capture

import br.ufv.hashlens.R
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.TxCost
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.domain.usecase.RegistrationProgress
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.ui.common.SepoliaEthFormat
import br.ufv.hashlens.ui.components.TechnicalItem
import java.math.BigDecimal

// Conversões puras do fluxo de captura: domínio → estado da tela (CaptureViewModel)

/** Detalhes técnicos do registro confirmado (FR-033), com o link da transação no explorador. */
internal fun technicalItems(image: LocalImageEntity, config: ChainConfig): List<TechnicalItem> = listOfNotNull(
    image.recordId?.let { TechnicalItem(R.string.technical_record, "#$it") },
    TechnicalItem(R.string.technical_sha256, image.sha256Hex),
    TechnicalItem(R.string.technical_phash, image.pHashHex),
    TechnicalItem(R.string.technical_wallet, image.walletAddress),
    image.deviceId?.let { TechnicalItem(R.string.technical_device, "#$it") },
    image.txHash?.let { TechnicalItem(R.string.technical_transaction, it, config.transactionUrl(it)) }
)

/**
 * T058: aviso quando a carteira muda com o pedido aberto nela (vínculo ou aprovação); `null` fora
 * dessas etapas ou sem mudança relevante.
 */
internal fun walletNotice(
    stage: RegistrationStage?,
    operationWallet: String,
    state: WalletState
): CaptureEffect.Notice? {
    val waitingForWallet = stage is RegistrationStage.BindingDevice || stage is RegistrationStage.AwaitingApproval
    return when {
        !waitingForWallet -> null
        state !is WalletState.Connected -> CaptureEffect.Notice(R.string.notice_wallet_disconnected)
        !state.address.equals(operationWallet, ignoreCase = true) ->
            CaptureEffect.Notice(R.string.notice_wallet_account_changed, AddressFormat.short(operationWallet))
        !state.isSepolia -> CaptureEffect.Notice(R.string.notice_wallet_network_changed)
        else -> null
    }
}

/** Etapa do Acompanhamento avisada pelo caso de uso antes de a transação sair (captura e edição). */
internal fun RegistrationProgress.toStage(): RegistrationStage = when (this) {
    is RegistrationProgress.Saved -> RegistrationStage.AwaitingApproval()
    is RegistrationProgress.BindingDevice -> RegistrationStage.BindingDevice(newKey)
    is RegistrationProgress.AwaitingWallet ->
        RegistrationStage.AwaitingApproval(SepoliaEthFormat.fromWei(cost.maxCost).normalized())
}

internal fun TxCost.toStage() = RegistrationStage.InsufficientFunds(
    cost = SepoliaEthFormat.fromWei(maxCost).normalized(),
    balance = SepoliaEthFormat.fromWei(balance).normalized()
)

internal fun FailureReason?.toFailure(): RegistrationFailure = when (this) {
    FailureReason.REJECTED -> RegistrationFailure.Rejected
    FailureReason.TIMEOUT -> RegistrationFailure.Timeout
    FailureReason.REVERTED -> RegistrationFailure.Reverted
    FailureReason.HASH_MISMATCH -> RegistrationFailure.HashMismatch
    // Saldo insuficiente chega como resultado próprio; aqui só por leitura do acervo
    FailureReason.NETWORK, FailureReason.INSUFFICIENT_FUNDS, null -> RegistrationFailure.Network
}

/** Sem zeros à direita, para comparar e exibir valores em SepoliaETH. */
internal fun BigDecimal.normalized(): BigDecimal = stripTrailingZeros()

internal fun FlashMode.next(): FlashMode = FlashMode.entries[(ordinal + 1) % FlashMode.entries.size]
