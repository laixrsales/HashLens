package br.ufv.hashlens.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.components.AddressChip
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.StatusBadge
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme

/**
 * Início (ux.md §4.2; esboço em direcao-visual §7.1): a verificação é a ação primária, a única que
 * não precisa de conta e a única com botão cheio; as ações de quem fotografa vêm abaixo, em lista.
 */
@Composable
fun HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    val spacing = HashLensTheme.spacing
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
        ) {
            TopRow(state, onAction)
            Spacer(Modifier.height(spacing.xxxl))
            VerifySection(onAction)
            Spacer(Modifier.height(spacing.xxl))
            HorizontalDivider(thickness = HashLensTheme.sizes.divider, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(spacing.xl))
            PhotosSection(state.pendingCount, onAction)
            Spacer(Modifier.height(spacing.lg))
            TextButton(
                onClick = { onAction(HomeAction.HowItWorks) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.action_how_it_works), style = MaterialTheme.typography.labelLarge)
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = spacing.xs)
                        .size(HashLensTheme.sizes.smallIcon)
                )
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

/** Nome do app e chip da carteira; com fonte grande, o chip desce para a linha de baixo. */
@Composable
private fun TopRow(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HashLensTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false
        )
        AddressChip(state.wallet, onClick = { onAction(HomeAction.OpenWallet) })
    }
}

@Composable
private fun VerifySection(onAction: (HomeAction) -> Unit) {
    val spacing = HashLensTheme.spacing
    Column {
        Text(
            stringResource(R.string.home_question),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(spacing.md))
        Text(
            stringResource(R.string.home_question_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(spacing.xl))
        PrimaryButton(
            text = stringResource(R.string.action_verify_image),
            onClick = { onAction(HomeAction.VerifyImage) },
            icon = Icons.Outlined.Search
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            stringResource(R.string.accepted_formats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhotosSection(pendingCount: Int, onAction: (HomeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)) {
        Text(
            stringResource(R.string.home_your_photos),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = HashLensTheme.spacing.xs)
                .semantics { heading() }
        )
        HomeRow(
            icon = Icons.Outlined.PhotoCamera,
            title = stringResource(R.string.action_register_photo),
            detail = stringResource(R.string.home_register_detail),
            onClick = { onAction(HomeAction.RegisterPhoto) }
        )
        HomeRow(
            icon = Icons.Outlined.PhotoLibrary,
            title = stringResource(R.string.home_library),
            detail = stringResource(R.string.home_library_detail),
            onClick = { onAction(HomeAction.OpenLibrary) }
        ) {
            if (pendingCount > 0) {
                StatusBadge(
                    ImageStatus.Pending,
                    text = pluralStringResource(R.plurals.home_library_pending, pendingCount, pendingCount)
                )
            }
        }
        HomeRow(
            icon = Icons.Outlined.EditNote,
            title = stringResource(R.string.home_import),
            detail = stringResource(R.string.home_import_detail),
            onClick = { onAction(HomeAction.ImportForEdit) }
        )
    }
}

/** Linha de ação secundária: ícone, título, descrição (e um selo opcional) e seta. */
@Composable
private fun HomeRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    badge: @Composable () -> Unit = {}
) {
    val spacing = HashLensTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HashLensTheme.sizes.listItemMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            badge()
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun HomeConnectedPreview() = HashLensTheme {
    HomeScreen(HomeSamples.connected, onAction = {})
}

@Preview
@Composable
private fun HomeDisconnectedPreview() = HashLensTheme {
    HomeScreen(HomeSamples.disconnected, onAction = {})
}

@Preview
@Composable
private fun HomeWrongNetworkPreview() = HashLensTheme {
    HomeScreen(HomeSamples.wrongNetwork, onAction = {})
}

@Preview
@Composable
private fun HomePendingPreview() = HashLensTheme {
    HomeScreen(HomeSamples.pending, onAction = {})
}

/** Estados do Início (ux.md §4.2), compartilhados por previews e screenshot tests. */
object HomeSamples {
    val disconnected = HomeUiState(wallet = SampleData.Wallet.disconnected)
    val connected = HomeUiState(wallet = SampleData.Wallet.connected)
    val wrongNetwork = HomeUiState(wallet = SampleData.Wallet.wrongNetwork)
    val pending = HomeUiState(wallet = SampleData.Wallet.connected, pendingCount = SampleData.Library.PENDING_COUNT)
}
