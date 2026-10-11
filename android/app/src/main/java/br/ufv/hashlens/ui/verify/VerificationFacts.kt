package br.ufv.hashlens.ui.verify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.RecordView
import br.ufv.hashlens.domain.model.VerificationResult
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.ui.common.DateFormat
import br.ufv.hashlens.ui.common.PercentFormat
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.ui.components.StatusBadge
import br.ufv.hashlens.ui.theme.AtkinsonMono
import br.ufv.hashlens.ui.theme.HashLensTheme
import java.time.ZoneId

// Blocos de conteúdo do Resultado da verificação (direção visual §7.2): fatos e candidatos.

/** Bloco de fatos entre divisores finos, como num laudo (direção visual §4). */
@Composable
internal fun Section(content: @Composable ColumnScope.() -> Unit) {
    val spacing = HashLensTheme.spacing
    Spacer(Modifier.height(spacing.xl))
    HorizontalDivider(thickness = HashLensTheme.sizes.divider, color = MaterialTheme.colorScheme.outline)
    Column(
        modifier = Modifier.padding(vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        content = content
    )
    HorizontalDivider(thickness = HashLensTheme.sizes.divider, color = MaterialTheme.colorScheme.outline)
}

@Composable
internal fun OriginalFacts(record: RecordView, zone: ZoneId) {
    Fact(stringResource(R.string.fact_registered_by), shortAddress(record.registrant), isAddress = true)
    Fact(stringResource(R.string.fact_when), DateFormat.dateTime(record.timestamp, zone))
    Fact(stringResource(R.string.fact_device), stringResource(R.string.fact_device_value, record.deviceId))
}

@Composable
internal fun EditedFacts(record: RecordView, original: RecordView, zone: ZoneId) {
    Fact(stringResource(R.string.fact_edited_by), shortAddress(record.registrant), isAddress = true)
    Fact(stringResource(R.string.fact_when), DateFormat.dateTime(record.timestamp, zone))
    Fact(
        stringResource(R.string.fact_original_by),
        shortAddress(original.registrant),
        isAddress = true,
        detail = DateFormat.dateTime(original.timestamp, zone)
    )
    Fact(stringResource(R.string.fact_device), stringResource(R.string.fact_device_value, record.deviceId))
}

/** Rótulo à esquerda, valor à direita; com fonte grande, cada lado quebra a linha no próprio espaço. */
@Composable
private fun Fact(label: String, value: String, isAddress: Boolean = false, detail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.md)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT)
        )
        Column(Modifier.weight(VALUE_WEIGHT)) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.let {
                    if (isAddress) it.copy(fontFamily = AtkinsonMono) else it
                },
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
internal fun Candidates(
    candidates: List<VerificationResult.VisualMatch.Candidate>,
    zone: ZoneId,
    onAction: (VerificationResultAction) -> Unit
) {
    Text(
        stringResource(R.string.visual_match_candidates),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() }
    )
    candidates.forEach { candidate ->
        CandidateRow(candidate, zone, onClick = { onAction(VerificationResultAction.ViewHistory(candidate.record.id)) })
    }
}

/** Um registro visualmente igual; tocar abre o histórico dele. */
@Composable
private fun CandidateRow(candidate: VerificationResult.VisualMatch.Candidate, zone: ZoneId, onClick: () -> Unit) {
    val spacing = HashLensTheme.spacing
    val isOriginal = candidate.record.id == candidate.original.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HashLensTheme.sizes.listItemMinHeight)
            .clickable(
                onClickLabel = stringResource(R.string.action_view_history),
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StatusBadge(if (isOriginal) ImageStatus.Original else ImageStatus.Edited)
            Text(
                stringResource(R.string.visual_match_candidate_by, shortAddress(candidate.record.registrant)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                DateFormat.dateTime(candidate.record.timestamp, zone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isOriginal) {
                Text(
                    PercentFormat.alteration(
                        LocalResources.current,
                        candidate.percentFromOriginal,
                        isEditedVersion = true
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Decorativo: a linha já anuncia "Ver histórico da imagem"
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(HashLensTheme.sizes.icon)
        )
    }
}

/** Endereço abreviado que não quebra na reticência: com fonte grande, vai inteiro para a linha seguinte. */
private fun shortAddress(address: String): String = AddressFormat.short(address).replace("…", "…$WORD_JOINER")

private const val WORD_JOINER = '\u2060'

private const val LABEL_WEIGHT = 0.4f
private const val VALUE_WEIGHT = 0.6f
