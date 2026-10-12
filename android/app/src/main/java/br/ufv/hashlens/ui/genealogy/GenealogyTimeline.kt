package br.ufv.hashlens.ui.genealogy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.GenealogyNode
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.ui.common.DateFormat
import br.ufv.hashlens.ui.common.EditOperationFormat
import br.ufv.hashlens.ui.common.PercentFormat
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.ui.components.StatusBadge
import br.ufv.hashlens.ui.theme.HashLensTheme
import java.time.ZoneId

// Nós da linha do tempo do Histórico da imagem (GenealogyScreen)

private const val EXPANDED_ROTATION = 180f

/**
 * Um passo da linha do tempo: trilha com o marcador na cor do status à esquerda e os fatos à
 * direita. A imagem consultada fica num bloco destacado; as outras versões ficam recolhidas.
 */
@Composable
internal fun TimelineNode(
    entry: TimelineEntry,
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    thumbnail: ImageBitmap?,
    onAction: (GenealogyAction) -> Unit,
    zone: ZoneId
) {
    val spacing = HashLensTheme.spacing
    val status = entry.node.status
    val anchor = remember { MarkerAnchor() }
    Row(
        Modifier
            .height(IntrinsicSize.Min)
            .onGloballyPositioned { anchor.row = it }
    ) {
        Rail(status.color.content, anchor, isFirst = isFirst, isLast = isLast, emphasized = entry.isQueried)
        Column(
            Modifier
                .weight(1f)
                .padding(start = spacing.sm, bottom = if (isLast) 0.dp else spacing.xl)
                .then(
                    if (entry.isQueried) {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                            .padding(spacing.md)
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            if (entry.isQueried) {
                Text(
                    stringResource(R.string.genealogy_queried),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Facts(
                    entry.node,
                    zone,
                    Modifier.weight(1f),
                    badgeModifier = Modifier.onGloballyPositioned { anchor.badge = it },
                    onOpenTransaction = { onAction(GenealogyAction.OpenTransaction(it)) }
                )
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = stringResource(R.string.genealogy_photo_description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(start = spacing.md)
                            .size(HashLensTheme.sizes.genealogyThumbnail)
                            .clip(MaterialTheme.shapes.small)
                    )
                }
            }
            if (entry.hiddenCount > 0) {
                Branches(entry, expanded, onAction, zone)
            }
        }
    }
}

/** Selo, quem e quando, operações, alterações, aparelho (FR-028) e o link da transação (US5). */
@Composable
private fun Facts(
    node: GenealogyNode,
    zone: ZoneId,
    modifier: Modifier,
    badgeModifier: Modifier,
    onOpenTransaction: (String) -> Unit
) {
    val record = node.record
    val resources = LocalResources.current
    val isOriginal = node.percentFromParent == null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)) {
        StatusBadge(node.status, badgeModifier)
        Text(
            stringResource(
                if (isOriginal) R.string.genealogy_registered_by else R.string.genealogy_edited_by,
                AddressFormat.short(record.registrant)
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Secondary(DateFormat.dateTime(record.timestamp, zone))
        if (record.operations.isNotEmpty()) {
            Text(
                EditOperationFormat.list(resources, record.operations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        node.percentFromParent?.let { Secondary(alteration(it, fromParent = true)) }
        if (!isOriginal) Secondary(alteration(node.percentFromOriginal, fromParent = false))
        Secondary(stringResource(R.string.genealogy_device, record.deviceId))
        if (!node.signatureValid) {
            Text(
                stringResource(R.string.genealogy_invalid_signature),
                style = MaterialTheme.typography.bodyMedium,
                color = HashLensTheme.statusColors.tampered.content
            )
        }
        // Sem link quando a transação não é conhecida (ex.: eventos indisponíveis no RPC)
        record.txUrl?.let { TransactionLink(it, onOpenTransaction) }
    }
}

/** "Ver registro na rede" (glossário, ux.md §6): abre a transação no explorador de blocos. */
@Composable
private fun TransactionLink(url: String, onOpen: (String) -> Unit) {
    TextButton(
        onClick = { onOpen(url) },
        shape = MaterialTheme.shapes.medium,
        // Sem recuo à esquerda: o link alinha com os dados acima
        contentPadding = PaddingValues(end = HashLensTheme.spacing.sm)
    ) {
        Text(stringResource(R.string.genealogy_open_transaction), style = MaterialTheme.typography.labelLarge)
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

@Composable
private fun Secondary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun alteration(percent: Double, fromParent: Boolean): String = when {
    percent == 0.0 && fromParent -> stringResource(R.string.genealogy_none_from_parent)
    percent == 0.0 -> stringResource(R.string.genealogy_none_from_original)
    fromParent -> stringResource(R.string.genealogy_from_parent, PercentFormat.number(percent))
    else -> stringResource(R.string.genealogy_from_original, PercentFormat.number(percent))
}

/** "+2 outras versões": recolhidas por padrão; cada uma abre o próprio histórico. */
@Composable
private fun Branches(entry: TimelineEntry, expanded: Boolean, onAction: (GenealogyAction) -> Unit, zone: ZoneId) {
    val spacing = HashLensTheme.spacing
    val label = if (entry.isQueried) {
        pluralStringResource(R.plurals.genealogy_derived_versions, entry.hiddenCount, entry.hiddenCount)
    } else {
        pluralStringResource(R.plurals.genealogy_other_versions, entry.hiddenCount, entry.hiddenCount)
    }
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HashLensTheme.sizes.minTouchTarget)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button) { onAction(GenealogyAction.ToggleBranches(entry.node.record.id)) }
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // Decorativo: o estado vai na semântica da linha
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (expanded) EXPANDED_ROTATION else 0f)
        )
    }
    if (expanded) {
        Column(Modifier.padding(start = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            entry.branches.forEach { BranchRow(it, onAction, zone) }
        }
    }
}

/** Uma versão fora do caminho, compacta; tocar abre o histórico dela. */
@Composable
private fun BranchRow(node: GenealogyNode, onAction: (GenealogyAction) -> Unit, zone: ZoneId) {
    val derived = node.size() - 1
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HashLensTheme.sizes.listItemMinHeight)
            .clickable(
                onClickLabel = stringResource(R.string.genealogy_open_record),
                role = Role.Button
            ) { onAction(GenealogyAction.OpenRecord(node.record.id)) }
            .padding(vertical = HashLensTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs)) {
            StatusBadge(node.status)
            Text(
                stringResource(R.string.genealogy_edited_by, AddressFormat.short(node.record.registrant)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Secondary(DateFormat.dateTime(node.record.timestamp, zone))
            Secondary(alteration(node.percentFromOriginal, fromParent = false))
            if (derived > 0) {
                Secondary(pluralStringResource(R.plurals.genealogy_derived_versions, derived, derived))
            }
        }
        // Decorativo: a linha já anuncia "Ver histórico desta versão"
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Meio do selo de status medido a partir do topo do passo, para o marcador acompanhar o selo com
 * fonte grande e dentro do bloco destacado da imagem consultada.
 */
private class MarkerAnchor {
    var row: LayoutCoordinates? = null
        set(value) {
            field = value
            update()
        }
    var badge: LayoutCoordinates? = null
        set(value) {
            field = value
            update()
        }

    /** Em px; `NaN` até as duas medidas chegarem. */
    var y by mutableFloatStateOf(Float.NaN)
        private set

    private fun update() {
        val row = row?.takeIf { it.isAttached } ?: return
        val badge = badge?.takeIf { it.isAttached } ?: return
        y = row.localPositionOf(badge, Offset.Zero).y + badge.size.height / 2f
    }
}

/**
 * Trilha da linha do tempo: o traço liga os passos e o marcador fica na altura do selo. A imagem
 * consultada tem o marcador maior, com anel.
 */
@Composable
private fun Rail(color: Color, anchor: MarkerAnchor, isFirst: Boolean, isLast: Boolean, emphasized: Boolean) {
    val sizes = HashLensTheme.sizes
    val outline = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .width(sizes.timelineRail)
            .fillMaxHeight()
            .drawBehind {
                val x = size.width / 2
                val markerY = anchor.y.takeUnless { it.isNaN() } ?: sizes.timelineMarkerOffset.toPx()
                val radius = (if (emphasized) sizes.timelineMarkerLarge else sizes.timelineMarker).toPx() / 2
                val line = sizes.divider.toPx() * 2
                if (!isFirst) drawLine(outline, Offset(x, 0f), Offset(x, markerY), line)
                if (!isLast) drawLine(outline, Offset(x, markerY), Offset(x, size.height), line)
                drawCircle(color, radius, Offset(x, markerY))
                if (emphasized) drawCircle(color, radius + line * 2, Offset(x, markerY), style = Stroke(line))
            }
    )
}

/** Mesma palavra, ícone e cor do vocabulário de status (ux.md §5); assinatura inválida prevalece. */
internal val GenealogyNode.status: ImageStatus
    get() = when {
        !signatureValid -> ImageStatus.Tampered
        percentFromParent == null -> ImageStatus.Original
        else -> ImageStatus.Edited
    }
