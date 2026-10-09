package br.ufv.hashlens.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.ui.preview.ComponentPreview
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme

/** Situação da carteira mostrada no chip do topo do Início (ux.md §4.2). */
sealed interface WalletChipState {
    data object Disconnected : WalletChipState

    data class Connected(val address: String) : WalletChipState

    /** Conectada fora da Sepolia: aviso em Vermelho óxido, com ícone e palavra. */
    data class WrongNetwork(val address: String) : WalletChipState
}

/**
 * Chip da carteira: "Conectar carteira", o endereço abreviado (`0x12ab…9f3c`) ou "Rede errada".
 * O endereço completo fica na tela Carteira e nos detalhes técnicos.
 */
@Composable
fun AddressChip(state: WalletChipState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val alert = HashLensTheme.statusColors.failed.content
    val label: String
    val description: String
    when (state) {
        WalletChipState.Disconnected -> {
            label = stringResource(R.string.wallet_connect)
            description = label
        }
        is WalletChipState.Connected -> {
            label = AddressFormat.short(state.address)
            description = stringResource(R.string.wallet_chip_connected_description, label)
        }
        is WalletChipState.WrongNetwork -> {
            label = stringResource(R.string.wallet_wrong_network)
            description =
                stringResource(R.string.wallet_chip_wrong_network_description, AddressFormat.short(state.address))
        }
    }
    val wrongNetwork = state is WalletChipState.WrongNetwork
    val contentColor = if (wrongNetwork) alert else MaterialTheme.colorScheme.onSurface

    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                if (wrongNetwork) Icons.Outlined.WarningAmber else Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
                tint = contentColor
            )
        },
        shape = MaterialTheme.shapes.medium,
        colors = AssistChipDefaults.assistChipColors(labelColor = contentColor, containerColor = Color.Transparent),
        border = BorderStroke(
            HashLensTheme.sizes.divider,
            if (wrongNetwork) alert else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick {
                onClick()
                true
            }
        }
    )
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun AddressChipPreview() = ComponentPreview {
    AddressChipSamples.All()
}

/** Os três estados do chip, compartilhados por previews e screenshot tests. */
object AddressChipSamples {
    @Composable
    fun All() = SampleColumn {
        AddressChip(WalletChipState.Disconnected, onClick = {})
        AddressChip(WalletChipState.Connected(SampleData.Wallets.AUTHOR), onClick = {})
        AddressChip(WalletChipState.WrongNetwork(SampleData.Wallets.AUTHOR), onClick = {})
    }
}
