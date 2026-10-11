package br.ufv.hashlens.ui.verify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.RecordView
import br.ufv.hashlens.domain.model.VerificationResult
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.ui.common.PercentFormat
import br.ufv.hashlens.ui.components.AlterationMeter
import br.ufv.hashlens.ui.components.ErrorState
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.SecondaryButton
import br.ufv.hashlens.ui.components.StatusHeader
import br.ufv.hashlens.ui.components.TechnicalDetails
import br.ufv.hashlens.ui.components.TechnicalItem
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.preview.SampleData.Percent
import br.ufv.hashlens.ui.preview.SampleData.Records
import br.ufv.hashlens.ui.theme.HashLensTheme
import java.time.ZoneId

/**
 * Resultado da verificação (ux.md §4.8; direção visual §7.2): (1) status com ícone e título;
 * (2) a frase que explica o que significa; (3) os fatos em linguagem leiga; (4) o histórico;
 * (5) os detalhes técnicos; (6) o rodapé obrigatório dos resultados positivos (Constituição V).
 *
 * @param zone fuso das datas; os previews fixam um para serem determinísticos.
 */
@Composable
fun VerificationResultScreen(
    state: VerificationResultUiState,
    onAction: (VerificationResultAction) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    val spacing = HashLensTheme.spacing
    val result = state.result
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
        ) {
            VerifyTopBar(
                title = stringResource(R.string.verification_result_title),
                onBack = { onAction(VerificationResultAction.Back) }
            )
            Spacer(Modifier.height(spacing.lg))
            if (result is VerificationResult.Error) {
                Failure(result.reason, onAction)
            } else {
                Answer(state, onAction, zone)
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

/** Resultados com resposta: cabeçalho de status, imagem sob exame, fatos, ações e rodapé. */
@Composable
private fun ColumnScope.Answer(
    state: VerificationResultUiState,
    onAction: (VerificationResultAction) -> Unit,
    zone: ZoneId
) {
    val spacing = HashLensTheme.spacing
    val result = state.result
    val status = result.status
    StatusHeader(status, result.message())
    if (state.thumbnail != null) {
        Spacer(Modifier.height(spacing.xl))
        ExaminedImage(state.thumbnail, status.color.content, Modifier.align(Alignment.CenterHorizontally))
    }
    when (result) {
        is VerificationResult.RegisteredOriginal -> {
            Section { OriginalFacts(result.record, zone) }
            HistoryAction(result.record.id, onAction)
        }
        is VerificationResult.RegisteredEdited -> {
            Spacer(Modifier.height(spacing.xl))
            // Com 0%, o aviso de que o arquivo não é idêntico já está no cabeçalho
            AlterationMeter(result.percentFromOriginal, isEditedVersion = false)
            Section { EditedFacts(result.record, result.original, zone) }
            HistoryAction(result.record.id, onAction)
        }
        is VerificationResult.VisualMatch -> Section { Candidates(result.candidates, zone, onAction) }
        else -> Unit
    }
    if (result.isPositive) {
        // Logo abaixo de "Ver histórico da imagem"; na correspondência visual, depois da lista
        val hasHistoryAction = result !is VerificationResult.VisualMatch
        Spacer(Modifier.height(if (hasHistoryAction) spacing.md else spacing.xl))
        SecondaryButton(
            stringResource(R.string.action_verify_another),
            onClick = { onAction(VerificationResultAction.VerifyAnother) }
        )
    } else {
        Spacer(Modifier.height(spacing.xxl))
        PrimaryButton(
            stringResource(R.string.action_verify_another),
            onClick = { onAction(VerificationResultAction.VerifyAnother) }
        )
    }
    val technical = result.technicalItems()
    technical.takeIf { it.isNotEmpty() }?.let { items ->
        Spacer(Modifier.height(spacing.lg))
        var expanded by rememberSaveable { mutableStateOf(false) }
        TechnicalDetails(
            items = items,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onOpenUrl = { onAction(VerificationResultAction.OpenUrl(it)) }
        )
    }
    if (result.isPositive) {
        // Sem detalhes técnicos, o divisor do rodapé não encosta no botão
        if (technical.isEmpty()) Spacer(Modifier.height(spacing.lg))
        Footer()
    }
}

@Composable
private fun Failure(reason: Reason, onAction: (VerificationResultAction) -> Unit) {
    val spacing = HashLensTheme.spacing
    when (reason) {
        Reason.UNSUPPORTED_FILE -> ErrorState(
            icon = Icons.Outlined.BrokenImage,
            title = stringResource(R.string.verification_unsupported_title),
            message = stringResource(R.string.verification_unsupported_message),
            actionLabel = stringResource(R.string.action_choose_another_image),
            onAction = { onAction(VerificationResultAction.VerifyAnother) }
        )
        Reason.NETWORK, Reason.UNKNOWN -> {
            ErrorState(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.verification_network_title),
                message = stringResource(
                    if (reason == Reason.NETWORK) {
                        R.string.verification_network_message
                    } else {
                        R.string.verification_unknown_message
                    }
                )
            )
            Spacer(Modifier.height(spacing.lg))
            PrimaryButton(stringResource(R.string.action_try_again), onClick = {
                onAction(VerificationResultAction.Retry)
            })
            Spacer(Modifier.height(spacing.md))
            SecondaryButton(stringResource(R.string.action_choose_another_image), onClick = {
                onAction(VerificationResultAction.VerifyAnother)
            })
        }
    }
}

@Composable
private fun HistoryAction(recordId: Long, onAction: (VerificationResultAction) -> Unit) {
    Spacer(Modifier.height(HashLensTheme.spacing.xl))
    PrimaryButton(
        stringResource(R.string.action_view_history),
        onClick = { onAction(VerificationResultAction.ViewHistory(recordId)) }
    )
}

/** Rodapé obrigatório (verification-result §3), ao fim da rolagem (direção visual §7.2). */
@Composable
private fun Footer() {
    HorizontalDivider(thickness = HashLensTheme.sizes.divider, color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(HashLensTheme.spacing.lg))
    Text(
        stringResource(R.string.verification_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val VerificationResult.isPositive: Boolean
    get() = this is VerificationResult.RegisteredOriginal ||
        this is VerificationResult.RegisteredEdited ||
        this is VerificationResult.VisualMatch

/** Mesma palavra, ícone e cor do vocabulário de status (ux.md §5). */
private val VerificationResult.status: ImageStatus
    get() = when (this) {
        is VerificationResult.RegisteredOriginal -> ImageStatus.Original
        is VerificationResult.RegisteredEdited -> ImageStatus.Edited
        is VerificationResult.VisualMatch -> ImageStatus.VisualMatch
        is VerificationResult.Tampered -> ImageStatus.Tampered
        VerificationResult.NotRegistered, is VerificationResult.Error -> ImageStatus.NotRegistered
    }

/** Mensagem obrigatória de cada resultado (verification-result §3). */
@Composable
private fun VerificationResult.message(): String = when (this) {
    is VerificationResult.RegisteredOriginal -> stringResource(R.string.verification_original_message)
    is VerificationResult.RegisteredEdited -> if (percentFromOriginal == 0.0) {
        stringResource(R.string.verification_edited_zero_message)
    } else {
        stringResource(R.string.verification_edited_message, PercentFormat.number(percentFromOriginal))
    }
    is VerificationResult.VisualMatch ->
        pluralStringResource(R.plurals.verification_visual_match_message, candidates.size, candidates.size)
    is VerificationResult.Tampered -> stringResource(R.string.verification_tampered_message)
    VerificationResult.NotRegistered -> stringResource(R.string.verification_not_registered_message)
    is VerificationResult.Error -> ""
}

/** Detalhes técnicos do registro exibido; a correspondência visual mostra os de cada um no histórico. */
internal fun VerificationResult.technicalItems(): List<TechnicalItem> = when (this) {
    is VerificationResult.RegisteredOriginal -> record.technicalItems()
    is VerificationResult.RegisteredEdited ->
        record.technicalItems() + TechnicalItem(R.string.technical_original_record, "#${original.id}")
    is VerificationResult.Tampered -> record.technicalItems()
    else -> emptyList()
}

private fun RecordView.technicalItems(): List<TechnicalItem> = listOfNotNull(
    TechnicalItem(R.string.technical_record, "#$id"),
    TechnicalItem(R.string.technical_sha256, sha256),
    TechnicalItem(R.string.technical_phash, pHash),
    TechnicalItem(R.string.technical_wallet, registrant),
    TechnicalItem(R.string.technical_device, "#$deviceId"),
    txUrl?.let { TechnicalItem(R.string.technical_transaction, it.substringAfterLast('/'), it) }
)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ResultOriginalPreview() = ResultPreview(VerificationResultSamples.original)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun ResultEditedPreview() = ResultPreview(VerificationResultSamples.edited)

@Preview
@Composable
private fun ResultEditedZeroPreview() = ResultPreview(VerificationResultSamples.editedZero)

@PreviewLightDark
@Composable
private fun ResultVisualMatchPreview() = ResultPreview(VerificationResultSamples.visualMatch)

@PreviewLightDark
@Composable
private fun ResultTamperedPreview() = ResultPreview(VerificationResultSamples.tampered)

@PreviewLightDark
@Composable
private fun ResultNotRegisteredPreview() = ResultPreview(VerificationResultSamples.notRegistered)

@PreviewLightDark
@Composable
private fun ResultNetworkPreview() = ResultPreview(VerificationResultSamples.network)

@Composable
private fun ResultPreview(state: VerificationResultUiState) = HashLensTheme {
    VerificationResultScreen(state, onAction = {}, zone = SampleData.ZONE)
}

/** Resultados de ux.md §4.8, compartilhados por previews e screenshot tests. */
object VerificationResultSamples {
    private fun state(result: VerificationResult, withImage: Boolean = true) =
        VerificationResultUiState(result, if (withImage) SampleData.Photo.landscape() else null)

    val original get() = state(VerificationResult.RegisteredOriginal(Records.original))
    val edited get() = state(VerificationResult.RegisteredEdited(Records.edited, Records.original, Percent.EDITED))
    val editedZero get() = state(
        VerificationResult.RegisteredEdited(Records.editedZero, Records.original, Percent.ZERO)
    )
    val visualMatch
        get() = state(
            VerificationResult.VisualMatch(
                listOf(
                    VerificationResult.VisualMatch.Candidate(Records.original, Records.original, Percent.ZERO),
                    VerificationResult.VisualMatch.Candidate(Records.edited, Records.original, Percent.EDITED)
                )
            )
        )
    val tampered get() = state(VerificationResult.Tampered(Records.tampered))
    val notRegistered get() = state(VerificationResult.NotRegistered)
    val network get() = state(VerificationResult.Error(Reason.NETWORK), withImage = false)
}
