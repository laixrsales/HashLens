package br.ufv.hashlens.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.DateFormat
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.SecondaryButton
import br.ufv.hashlens.ui.components.StatusBadge
import br.ufv.hashlens.ui.theme.HashLensTheme
import java.time.ZoneId

// Uma foto da grade de Meu acervo e a folha com as opções dela

/**
 * Miniatura, selo de status e data. Tocar abre as opções; nas que falharam, "Tentar de novo" fica
 * na própria célula, porque é a ação que a pessoa veio fazer.
 */
@Composable
internal fun LibraryCell(item: LibraryItem, onAction: (LibraryAction) -> Unit, zone: ZoneId) {
    val spacing = HashLensTheme.spacing
    val date = DateFormat.dateTime(item.savedAt, zone)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.library_open_item), role = Role.Button) {
                    onAction(LibraryAction.Select(item.localId))
                },
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Thumbnail(item, date)
            StatusBadge(item.status)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            item.retrying -> Text(
                stringResource(R.string.library_retrying),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .heightIn(min = HashLensTheme.sizes.minTouchTarget)
                    .padding(vertical = spacing.md)
            )
            item.canRetry -> SecondaryButton(
                stringResource(R.string.action_try_again),
                onClick = { onAction(LibraryAction.Retry(item.localId)) }
            )
        }
    }
}

/**
 * Foto quadrada; sem o arquivo na galeria, um aviso no lugar da imagem.
 *
 * @param compact miniatura pequena (folha de opções): o aviso fica só no ícone, porque a frase da
 *   folha explica o que houve.
 */
@Composable
private fun Thumbnail(item: LibraryItem, date: String, compact: Boolean = false) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            !item.fileAvailable -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs),
                modifier = Modifier.padding(HashLensTheme.spacing.sm)
            ) {
                // Decorativo: o texto abaixo diz o que houve
                Icon(
                    Icons.Outlined.HideImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!compact) {
                    Text(
                        stringResource(R.string.library_file_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item.thumbnail != null -> Image(
                bitmap = item.thumbnail,
                contentDescription = stringResource(R.string.library_photo_description, date),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Opções de uma foto: o que dá para fazer com ela depende do status (FR-015). */
@Composable
internal fun LibraryItemSheetContent(item: LibraryItem, onAction: (LibraryAction) -> Unit, zone: ZoneId) {
    val spacing = HashLensTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(HashLensTheme.sizes.genealogyThumbnail)) {
                Thumbnail(item, DateFormat.dateTime(item.savedAt, zone), compact = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StatusBadge(item.status)
                Text(
                    stringResource(R.string.library_item_taken, DateFormat.dateTime(item.savedAt, zone)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SheetActions(item, onAction)
    }
}

@Composable
private fun ColumnScope.SheetActions(item: LibraryItem, onAction: (LibraryAction) -> Unit) {
    val recordId = item.recordId
    when {
        item.status == ImageStatus.Pending -> Message(stringResource(R.string.library_item_pending_message))
        item.canRetry -> PrimaryButton(
            stringResource(R.string.action_try_again),
            onClick = { onAction(LibraryAction.Retry(item.localId)) }
        )
        item.retrying -> Message(stringResource(R.string.library_retrying))
    }
    if (recordId != null && !item.fileAvailable) Message(stringResource(R.string.library_item_unavailable_message))
    Spacer(Modifier.height(HashLensTheme.spacing.sm))
    if (item.canEdit) {
        PrimaryButton(stringResource(R.string.action_edit_photo), onClick = {
            onAction(LibraryAction.Edit(item.localId))
        })
    }
    if (recordId != null) {
        val history = stringResource(R.string.action_view_history)
        if (item.canEdit) {
            SecondaryButton(history, onClick = { onAction(LibraryAction.OpenHistory(recordId)) })
        } else {
            PrimaryButton(history, onClick = { onAction(LibraryAction.OpenHistory(recordId)) })
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
}

/**
 * Moldura de preview e screenshot da folha de opções: a tela de trás escurecida e a folha presa
 * embaixo, como o `ModalBottomSheet` aparece (ele abre numa janela própria, que o Robolectric não
 * captura). Mesmo recurso da `WalletSheetFrame`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemSheetFrame(item: LibraryItem, zone: ZoneId) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = BottomSheetDefaults.ScrimColor.alpha))
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                LibraryItemSheetContent(item, onAction = {}, zone = zone)
            }
        }
    }
}
