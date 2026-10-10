package br.ufv.hashlens.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.ui.common.SepoliaEthFormat
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.SecondaryButton
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme
import java.math.BigDecimal

/** Estados da folha "Conectar carteira" (ux.md §4.3). */
sealed interface WalletSheetUiState {
    /** Explica por que a carteira é necessária e quanto custa, antes de abrir o app dela. */
    data object Initial : WalletSheetUiState

    /** O app da carteira está aberto, aguardando a pessoa aprovar a conexão. */
    data object Connecting : WalletSheetUiState

    data class Connected(val address: String, val balance: BigDecimal) : WalletSheetUiState

    data class WrongNetwork(val address: String) : WalletSheetUiState

    /** Saldo de teste baixo demais para a taxa de um registro (FR-003). */
    data class LowBalance(val address: String, val balance: BigDecimal) : WalletSheetUiState

    data object NotInstalled : WalletSheetUiState

    data object Rejected : WalletSheetUiState
}

sealed interface WalletSheetAction {
    /** Abre a lista de carteiras do AppKit (também em "Tentar de novo" e "Escolher uma carteira"). */
    data object Connect : WalletSheetAction

    data object Cancel : WalletSheetAction

    /** Carteira pronta: segue para a câmera. */
    data object Continue : WalletSheetAction

    data object SwitchToSepolia : WalletSheetAction

    /** Abre o faucet da Sepolia no navegador. */
    data object OpenFaucet : WalletSheetAction

    data object RefreshBalance : WalletSheetAction

    data object Dismiss : WalletSheetAction
}

/** Folha "Conectar carteira": aparece por cima da tela que pediu a carteira (ex.: Registrar foto). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSheet(state: WalletSheetUiState, onAction: (WalletSheetAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onAction(WalletSheetAction.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = sheetShape(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        WalletSheetContent(state, onAction, Modifier.navigationBarsPadding())
    }
}

/** Conteúdo da folha, sem o `ModalBottomSheet` (que precisa de janela própria): usado também nos previews. */
@Composable
fun WalletSheetContent(
    state: WalletSheetUiState,
    onAction: (WalletSheetAction) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is WalletSheetUiState.Connected, is WalletSheetUiState.WrongNetwork, is WalletSheetUiState.LowBalance ->
            AccountContent(state, onAction, modifier)
        else -> ConnectionContent(state, onAction, modifier)
    }
}

/** Antes de haver conta: explicação, espera, carteira ausente ou recusa. */
@Composable
private fun ConnectionContent(state: WalletSheetUiState, onAction: (WalletSheetAction) -> Unit, modifier: Modifier) {
    val alert = HashLensTheme.statusColors.failed.content
    val neutral = MaterialTheme.colorScheme.onSurface
    when (state) {
        WalletSheetUiState.Initial -> SheetLayout(
            icon = Icons.Outlined.AccountBalanceWallet,
            iconTint = neutral,
            title = stringResource(R.string.wallet_connect),
            primary = stringResource(R.string.wallet_connect) to WalletSheetAction.Connect,
            onAction = onAction,
            modifier = modifier
        ) {
            Body(stringResource(R.string.wallet_sheet_why))
            Body(stringResource(R.string.wallet_sheet_cost))
        }

        WalletSheetUiState.Connecting -> SheetLayout(
            icon = null,
            iconTint = neutral,
            title = stringResource(R.string.wallet_connecting_title),
            secondary = stringResource(R.string.action_cancel) to WalletSheetAction.Cancel,
            onAction = onAction,
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.md)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(HashLensTheme.sizes.icon),
                    color = HashLensTheme.statusColors.pending.content,
                    strokeWidth = HashLensTheme.sizes.cornerMarkStroke
                )
                Body(stringResource(R.string.wallet_connecting_message), Modifier.weight(1f))
            }
        }

        WalletSheetUiState.NotInstalled -> SheetLayout(
            icon = Icons.Outlined.AccountBalanceWallet,
            iconTint = neutral,
            title = stringResource(R.string.wallet_not_installed_title),
            primary = stringResource(R.string.action_choose_wallet) to WalletSheetAction.Connect,
            onAction = onAction,
            modifier = modifier
        ) {
            Body(stringResource(R.string.wallet_not_installed_message))
        }

        WalletSheetUiState.Rejected -> SheetLayout(
            icon = Icons.Outlined.ErrorOutline,
            iconTint = alert,
            title = stringResource(R.string.wallet_rejected_title),
            primary = stringResource(R.string.action_try_again) to WalletSheetAction.Connect,
            onAction = onAction,
            modifier = modifier
        ) {
            Body(stringResource(R.string.wallet_rejected_message))
        }

        else -> Unit
    }
}

/** Conta conectada: pronta, em outra rede ou com saldo baixo. */
@Composable
private fun AccountContent(state: WalletSheetUiState, onAction: (WalletSheetAction) -> Unit, modifier: Modifier) {
    val alert = HashLensTheme.statusColors.failed.content
    val neutral = MaterialTheme.colorScheme.onSurface
    when (state) {
        is WalletSheetUiState.Connected -> SheetLayout(
            icon = Icons.Outlined.AccountBalanceWallet,
            iconTint = neutral,
            title = stringResource(R.string.wallet_connected_title),
            primary = stringResource(R.string.action_continue) to WalletSheetAction.Continue,
            onAction = onAction,
            modifier = modifier
        ) {
            Account(state.address)
            Body(stringResource(R.string.wallet_balance, SepoliaEthFormat.amount(state.balance)))
        }

        is WalletSheetUiState.WrongNetwork -> SheetLayout(
            icon = Icons.Outlined.WarningAmber,
            iconTint = alert,
            title = stringResource(R.string.wallet_wrong_network_title),
            primary = stringResource(R.string.action_switch_to_sepolia) to WalletSheetAction.SwitchToSepolia,
            onAction = onAction,
            modifier = modifier
        ) {
            Account(state.address)
            Body(stringResource(R.string.wallet_wrong_network_message))
        }

        is WalletSheetUiState.LowBalance -> SheetLayout(
            icon = Icons.Outlined.WarningAmber,
            iconTint = alert,
            title = stringResource(R.string.low_balance_title),
            primary = stringResource(R.string.action_get_test_balance) to WalletSheetAction.OpenFaucet,
            secondary = stringResource(R.string.action_check_balance_again) to WalletSheetAction.RefreshBalance,
            onAction = onAction,
            modifier = modifier
        ) {
            Account(state.address)
            Body(stringResource(R.string.wallet_low_balance_message, SepoliaEthFormat.amount(state.balance)))
        }

        else -> Unit
    }
}

/** Ícone e título, o corpo do estado e, no fim, a ação primária e a secundária. */
@Composable
private fun SheetLayout(
    icon: ImageVector?,
    iconTint: Color,
    title: String,
    onAction: (WalletSheetAction) -> Unit,
    modifier: Modifier = Modifier,
    primary: Pair<String, WalletSheetAction>? = null,
    secondary: Pair<String, WalletSheetAction>? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    val spacing = HashLensTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            // Decorativo: o título diz o estado
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
        }
        body()
        Spacer(Modifier.height(spacing.sm))
        primary?.let { (label, action) -> PrimaryButton(label, onClick = { onAction(action) }) }
        secondary?.let { (label, action) -> SecondaryButton(label, onClick = { onAction(action) }) }
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/** Endereço abreviado em Mono; o completo fica na tela Carteira (ux.md §4.11). */
@Composable
private fun Account(address: String) {
    Text(
        AddressFormat.short(address),
        style = HashLensTheme.typography.technical,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Topo com o raio `extraLarge` (16 dp, direção visual §4) e base reta, presa à borda da tela. */
@Composable
private fun sheetShape() =
    MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)

/**
 * Moldura de preview e screenshot: a tela de trás escurecida e a folha presa embaixo, como o
 * `ModalBottomSheet` aparece (ele abre numa janela própria, que o Robolectric não captura).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSheetFrame(state: WalletSheetUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = BottomSheetDefaults.ScrimColor.alpha))
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = sheetShape(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                WalletSheetContent(state, onAction = {})
            }
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun WalletSheetInitialPreview() = HashLensTheme { WalletSheetFrame(WalletSheetSamples.initial) }

@Preview
@Composable
private fun WalletSheetConnectingPreview() = HashLensTheme { WalletSheetFrame(WalletSheetSamples.connecting) }

@Preview
@Composable
private fun WalletSheetConnectedPreview() = HashLensTheme { WalletSheetFrame(WalletSheetSamples.connected) }

@Preview
@Composable
private fun WalletSheetWrongNetworkPreview() = HashLensTheme { WalletSheetFrame(WalletSheetSamples.wrongNetwork) }

@Preview
@Composable
private fun WalletSheetLowBalancePreview() = HashLensTheme { WalletSheetFrame(WalletSheetSamples.lowBalance) }

@Preview
@Composable
private fun WalletSheetNotInstalledPreview() = HashLensTheme { WalletSheetFrame(WalletSheetSamples.notInstalled) }

@Preview
@Composable
private fun WalletSheetRejectedPreview() = HashLensTheme { WalletSheetFrame(WalletSheetSamples.rejected) }

/** Estados da folha (ux.md §4.3), compartilhados por previews e screenshot tests. */
object WalletSheetSamples {
    val initial: WalletSheetUiState = WalletSheetUiState.Initial
    val connecting: WalletSheetUiState = WalletSheetUiState.Connecting
    val connected: WalletSheetUiState =
        WalletSheetUiState.Connected(SampleData.Wallets.AUTHOR, SampleData.Chain.BALANCE_OK)
    val wrongNetwork: WalletSheetUiState = WalletSheetUiState.WrongNetwork(SampleData.Wallets.AUTHOR)
    val lowBalance: WalletSheetUiState =
        WalletSheetUiState.LowBalance(SampleData.Wallets.AUTHOR, SampleData.Chain.BALANCE_LOW)
    val notInstalled: WalletSheetUiState = WalletSheetUiState.NotInstalled
    val rejected: WalletSheetUiState = WalletSheetUiState.Rejected
}
