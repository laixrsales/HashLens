package br.ufv.hashlens.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.DateFormat
import br.ufv.hashlens.ui.components.EmptyState
import br.ufv.hashlens.ui.components.MessageState
import br.ufv.hashlens.ui.theme.HashLensTheme
import br.ufv.hashlens.ui.verify.VerifyTopBar
import java.time.ZoneId

/**
 * Meu acervo (ux.md §4.10; US6, FR-029): as fotos registradas neste aparelho em grade, agrupadas
 * pela original, com o selo de status de cada uma e "Tentar de novo" nas que falharam. Tocar numa
 * foto abre as opções dela (histórico, edição).
 *
 * @param zone fuso das datas; os previews fixam um para serem determinísticos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    val spacing = HashLensTheme.spacing
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(horizontal = spacing.lg)) {
                VerifyTopBar(title = stringResource(R.string.library_title), onBack = { onAction(LibraryAction.Back) })
            }
            when {
                state.loading -> Loading()
                state.isEmpty -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.library_empty_title),
                    message = stringResource(R.string.library_empty_message),
                    actionLabel = stringResource(R.string.action_register_photo),
                    onAction = { onAction(LibraryAction.RegisterPhoto) },
                    modifier = Modifier.padding(spacing.lg)
                )
                else -> {
                    Filters(state, onAction)
                    if (state.groups.isEmpty()) EmptyFilter(state.filter) else Grid(state, onAction, zone)
                }
            }
        }
    }
    state.selectedItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { onAction(LibraryAction.DismissSelection) },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            LibraryItemSheetContent(item, onAction, zone)
        }
    }
}

/**
 * Todas, pendentes e com falha (ux.md §4.10), com a contagem do acervo inteiro. Quebram em mais
 * linhas quando não cabem (fonte grande), em vez de sair da tela.
 */
@Composable
private fun Filters(state: LibraryUiState, onAction: (LibraryAction) -> Unit) {
    val spacing = HashLensTheme.spacing
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        LibraryFilter.entries.forEach { filter ->
            val name = stringResource(filter.label)
            val count = when (filter) {
                LibraryFilter.All -> null
                LibraryFilter.Pending -> state.pendingCount
                LibraryFilter.Failed -> state.failedCount
            }
            FilterChip(
                selected = state.filter == filter,
                onClick = { onAction(LibraryAction.SetFilter(filter)) },
                label = {
                    Text(
                        if (count == null) name else stringResource(R.string.library_filter_count, name, count),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                shape = MaterialTheme.shapes.medium,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.heightIn(min = HashLensTheme.sizes.minTouchTarget)
            )
        }
    }
}

private val LibraryFilter.label: Int
    get() = when (this) {
        LibraryFilter.All -> R.string.library_filter_all
        LibraryFilter.Pending -> R.string.library_filter_pending
        LibraryFilter.Failed -> R.string.library_filter_failed
    }

@Composable
private fun Grid(state: LibraryUiState, onAction: (LibraryAction) -> Unit, zone: ZoneId) {
    val spacing = HashLensTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(HashLensTheme.sizes.libraryCellMinWidth),
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        state.groups.forEachIndexed { index, group ->
            item(key = "group-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                GroupHeader(group, first = index == 0, zone = zone)
            }
            items(group.items, key = { it.localId }) { item -> LibraryCell(item, onAction, zone) }
        }
    }
}

/** Divisor fino entre grupos, como num laudo (direção visual §4), e de qual original eles são. */
@Composable
private fun GroupHeader(group: LibraryGroup, first: Boolean, zone: ZoneId) {
    val spacing = HashLensTheme.spacing
    Column(Modifier.padding(top = if (first) spacing.sm else spacing.md)) {
        if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            group.originalTakenAt?.let {
                stringResource(R.string.library_group_original, DateFormat.dateTime(it, zone))
            }
                ?: stringResource(R.string.library_group_imported),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = if (first) 0.dp else spacing.lg)
                .semantics { heading() }
        )
    }
}

/** Filtro sem fotos: diz o que apareceria ali. */
@Composable
private fun EmptyFilter(filter: LibraryFilter) {
    val (title, message) = when (filter) {
        LibraryFilter.Failed -> R.string.library_empty_failed_title to R.string.library_empty_failed_message
        else -> R.string.library_empty_pending_title to R.string.library_empty_pending_message
    }
    MessageState(
        icon = Icons.Outlined.PhotoLibrary,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(title),
        message = stringResource(message),
        modifier = Modifier.padding(HashLensTheme.spacing.lg)
    )
}

/** Esperas sempre com texto (direção visual §6). */
@Composable
private fun Loading() {
    val spacing = HashLensTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(HashLensTheme.sizes.minTouchTarget),
            strokeWidth = HashLensTheme.sizes.cornerMarkStroke
        )
        Text(
            stringResource(R.string.library_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
