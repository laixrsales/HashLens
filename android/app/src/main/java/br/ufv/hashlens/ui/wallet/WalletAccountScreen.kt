package br.ufv.hashlens.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import br.ufv.hashlens.ui.verify.VerifyTopBar
import java.math.BigDecimal

/**
 * Estado da tela Carteira (ux.md §4.11).
 *
 * @property balance saldo de teste; `null` enquanto consulta ou se não foi possível consultar.
 * @property balanceUnavailable sem rede para consultar o saldo.
 * @property lowBalance o saldo não paga a taxa de um registro (FR-003).
 */
data class WalletAccountUiState(
    val address: String,
    val onSepolia: Boolean = true,
    val balance: BigDecimal? = null,
    val balanceUnavailable: Boolean = false,
    val lowBalance: Boolean = false
)

sealed interface WalletAccountAction {
    data object Back : WalletAccountAction

    /** Copia o endereço completo, para conferir ou pedir saldo de teste. */
    data object CopyAddress : WalletAccountAction

    data object SwitchToSepolia : WalletAccountAction

    data object OpenFaucet : WalletAccountAction

    data object Disconnect : WalletAccountAction
}

/**
 * Carteira (ux.md §4.11): a conta conectada, a rede, o saldo de teste e "Desconectar". Fatos em
 * linhas com rótulo e valor, separados por divisores, como no Resultado (direção visual §4). Só um
 * problema por vez ganha a ação primária: rede errada, depois saldo baixo.
 */
@Composable
fun WalletAccountScreen(
    state: WalletAccountUiState,
    onAction: (WalletAccountAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = HashLensTheme.spacing
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            VerifyTopBar(title = stringResource(R.string.wallet_account_title), onBack = {
                onAction(WalletAccountAction.Back)
            })
            Spacer(Modifier.height(spacing.sm))
            Address(state.address, onCopy = { onAction(WalletAccountAction.CopyAddress) })
            Divider()
            Network(state.onSepolia)
            Divider()
            Balance(state)
            Divider()
            Spacer(Modifier.height(spacing.md))
            when {
                !state.onSepolia -> PrimaryButton(
                    stringResource(R.string.action_switch_to_sepolia),
                    onClick = { onAction(WalletAccountAction.SwitchToSepolia) }
                )
                state.lowBalance -> PrimaryButton(
                    stringResource(R.string.action_get_test_balance),
                    onClick = { onAction(WalletAccountAction.OpenFaucet) }
                )
                else -> FaucetLink(onClick = { onAction(WalletAccountAction.OpenFaucet) })
            }
            Spacer(Modifier.height(spacing.xl))
            SecondaryButton(stringResource(R.string.wallet_account_disconnect), onClick = {
                onAction(WalletAccountAction.Disconnect)
            })
            Text(
                stringResource(R.string.wallet_account_disconnect_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun Address(address: String, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Fact(stringResource(R.string.wallet_account_address), Modifier.weight(1f)) {
            Text(
                AddressFormat.short(address),
                style = HashLensTheme.typography.technical,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.wallet_account_copy))
        }
    }
}

@Composable
private fun Network(onSepolia: Boolean) {
    Fact(stringResource(R.string.wallet_account_network)) {
        if (onSepolia) {
            Value(stringResource(R.string.wallet_account_sepolia))
        } else {
            Warning(stringResource(R.string.wallet_wrong_network))
            Secondary(stringResource(R.string.wallet_wrong_network_message))
        }
    }
}

@Composable
private fun Balance(state: WalletAccountUiState) {
    Fact(stringResource(R.string.wallet_account_balance)) {
        val balance = state.balance
        when {
            balance == null && state.balanceUnavailable ->
                Secondary(stringResource(R.string.wallet_account_balance_unavailable))
            balance == null -> Secondary(stringResource(R.string.wallet_account_balance_loading))
            state.lowBalance -> {
                Warning(stringResource(R.string.wallet_account_balance_value, SepoliaEthFormat.amount(balance)))
                Secondary(stringResource(R.string.wallet_account_low_balance))
            }
            else -> Value(stringResource(R.string.wallet_account_balance_value, SepoliaEthFormat.amount(balance)))
        }
    }
}

/** Rótulo em Cinza 18% e o valor abaixo (direção visual §7.2). */
@Composable
private fun Fact(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Secondary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Problema da conta: ícone e texto em Vermelho óxido, nunca só a cor. */
@Composable
private fun Warning(text: String) {
    val color = HashLensTheme.statusColors.failed.content
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)
    ) {
        // Decorativo: o texto diz o problema
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(HashLensTheme.sizes.icon)
        )
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** Sem problema na conta, o faucet fica como link: não é a ação da tela. */
@Composable
private fun FaucetLink(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        // Sem recuo à esquerda: o link alinha com os fatos acima
        contentPadding = PaddingValues(end = HashLensTheme.spacing.sm)
    ) {
        Text(stringResource(R.string.action_get_test_balance), style = MaterialTheme.typography.labelLarge)
        // Decorativo: o texto já diz o que o link faz
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier
                .padding(start = HashLensTheme.spacing.xs)
                .size(HashLensTheme.sizes.smallIcon)
        )
    }
}

/** Estados da tela Carteira (ux.md §4.11), compartilhados por previews e screenshot tests. */
object WalletAccountSamples {
    val connected = WalletAccountUiState(SampleData.Wallets.AUTHOR, balance = SampleData.Chain.BALANCE_OK)
    val balanceLoading = WalletAccountUiState(SampleData.Wallets.AUTHOR)
    val balanceUnavailable = WalletAccountUiState(SampleData.Wallets.AUTHOR, balanceUnavailable = true)
    val wrongNetwork =
        WalletAccountUiState(SampleData.Wallets.AUTHOR, onSepolia = false, balance = SampleData.Chain.BALANCE_OK)
    val lowBalance =
        WalletAccountUiState(SampleData.Wallets.AUTHOR, balance = SampleData.Chain.BALANCE_LOW, lowBalance = true)
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun WalletAccountConnectedPreview() = WalletAccountPreview(WalletAccountSamples.connected)

@Preview
@Composable
private fun WalletAccountLoadingPreview() = WalletAccountPreview(WalletAccountSamples.balanceLoading)

@PreviewLightDark
@Composable
private fun WalletAccountWrongNetworkPreview() = WalletAccountPreview(WalletAccountSamples.wrongNetwork)

@PreviewLightDark
@Composable
private fun WalletAccountLowBalancePreview() = WalletAccountPreview(WalletAccountSamples.lowBalance)

@Composable
private fun WalletAccountPreview(state: WalletAccountUiState) =
    HashLensTheme { WalletAccountScreen(state, onAction = {}) }
