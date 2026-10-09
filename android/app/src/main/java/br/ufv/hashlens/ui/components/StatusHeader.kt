package br.ufv.hashlens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.PercentFormat
import br.ufv.hashlens.ui.preview.ComponentPreview
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme

/**
 * Resposta da tela (Resultado, Acompanhamento): ícone e título na cor do status, e a frase que
 * explica o que ele significa. É o único bloco com fundo nas telas (direção visual §4).
 *
 * @param title padrão: a palavra do status; "Foto registrada" usa [ImageStatus.Original] com outro título.
 */
@Composable
fun StatusHeader(
    status: ImageStatus,
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(status.title)
) {
    val color = status.color
    val spacing = HashLensTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color.container, MaterialTheme.shapes.large)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            // Decorativo: o título já diz o status
            Icon(
                status.icon,
                contentDescription = null,
                tint = color.content,
                modifier = Modifier.size(HashLensTheme.sizes.statusIcon)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = color.content,
                modifier = Modifier.semantics { heading() }
            )
        }
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun StatusHeaderResultsPreview() = ComponentPreview {
    StatusHeaderSamples.Results()
}

@Preview
@Composable
private fun StatusHeaderRegistrationPreview() = ComponentPreview {
    StatusHeaderSamples.Registration()
}

/** Todos os estados do cabeçalho, compartilhados por previews e screenshot tests. */
object StatusHeaderSamples {
    @Composable
    fun Results() = SampleColumn {
        StatusHeader(ImageStatus.Original, stringResource(R.string.verification_original_message))
        StatusHeader(
            ImageStatus.Edited,
            stringResource(R.string.verification_edited_message, PercentFormat.number(SampleData.Percent.EDITED))
        )
        StatusHeader(ImageStatus.Edited, stringResource(R.string.verification_edited_zero_message))
        StatusHeader(
            ImageStatus.VisualMatch,
            pluralStringResource(
                R.plurals.verification_visual_match_message,
                SampleData.Records.visualMatchCandidates.size,
                SampleData.Records.visualMatchCandidates.size
            )
        )
        StatusHeader(ImageStatus.Tampered, stringResource(R.string.verification_tampered_message))
        StatusHeader(ImageStatus.NotRegistered, stringResource(R.string.verification_not_registered_message))
    }

    @Composable
    fun Registration() = SampleColumn {
        StatusHeader(ImageStatus.Pending, stringResource(R.string.registration_pending_message))
        StatusHeader(
            ImageStatus.Original,
            stringResource(
                R.string.registration_confirmed_message,
                SampleData.dateTime(SampleData.Records.original.timestamp)
            ),
            title = stringResource(R.string.status_photo_registered)
        )
        StatusHeader(ImageStatus.Failed, stringResource(R.string.registration_failed_message))
    }
}
