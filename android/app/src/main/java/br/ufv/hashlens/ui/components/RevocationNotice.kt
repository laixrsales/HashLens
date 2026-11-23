package br.ufv.hashlens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.RevocationStatus
import br.ufv.hashlens.ui.preview.ComponentPreview
import br.ufv.hashlens.ui.theme.HashLensTheme

/**
 * Situação da revogação do certificado do aparelho (020 FR-017): aviso com ícone, título e frase
 * quando foi revogado depois do vínculo; nota discreta quando a lista não pôde ser consultada; nada
 * quando não foi revogado. Sem fundo, porque o cabeçalho de status é o único bloco com fundo
 * (direção visual §4); o âmbar de "cautela" vem sempre com o ícone e o título.
 *
 * @param compact versão de uma linha para itens de lista (candidatos, nós do histórico).
 */
@Composable
fun RevocationNotice(revocation: RevocationStatus, modifier: Modifier = Modifier, compact: Boolean = false) {
    when (revocation) {
        RevocationStatus.REVOKED_AFTER_BINDING -> if (compact) RevokedLine(modifier) else RevokedWarning(modifier)
        RevocationStatus.UNKNOWN -> if (compact) Unit else UnknownNote(modifier)
        RevocationStatus.NOT_REVOKED -> Unit
    }
}

@Composable
private fun RevokedWarning(modifier: Modifier) {
    val spacing = HashLensTheme.spacing
    val color = HashLensTheme.statusColors.pending.content
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        // Decorativo: o título diz o que aconteceu
        Icon(
            Icons.Outlined.GppMaybe,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(HashLensTheme.sizes.icon)
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                stringResource(R.string.revocation_warning_title),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.revocation_warning_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RevokedLine(modifier: Modifier) {
    val color = HashLensTheme.statusColors.pending.content
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        // Alinhado à primeira linha: com fonte grande, a frase quebra
        Icon(
            Icons.Outlined.GppMaybe,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .padding(top = HashLensTheme.spacing.xs / 2)
                .size(HashLensTheme.sizes.smallIcon)
        )
        Text(
            stringResource(R.string.revocation_warning_short),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun UnknownNote(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(HashLensTheme.sizes.smallIcon)
        )
        Text(
            stringResource(R.string.revocation_unknown_note),
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun RevocationNoticePreview() = ComponentPreview { RevocationNoticeSamples.All() }

/** Os três formatos, compartilhados por preview e screenshot test. */
object RevocationNoticeSamples {
    @Composable
    fun All() {
        Column(verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xl)) {
            RevocationNotice(RevocationStatus.REVOKED_AFTER_BINDING)
            RevocationNotice(RevocationStatus.REVOKED_AFTER_BINDING, compact = true)
            RevocationNotice(RevocationStatus.UNKNOWN)
        }
    }
}
