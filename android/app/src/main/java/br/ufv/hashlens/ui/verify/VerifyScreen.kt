package br.ufv.hashlens.ui.verify

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.components.ErrorState
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.ProgressStep
import br.ufv.hashlens.ui.components.StepProgress
import br.ufv.hashlens.ui.components.StepState
import br.ufv.hashlens.ui.components.cornerMarks
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme

/**
 * Verificar imagem (ux.md §4.7): a imagem a verificar vem só do Photo Picker (FR-019). Enquanto
 * analisa, mostra a imagem sob exame e as etapas; arquivo não suportado nomeia os formatos aceitos.
 */
@Composable
fun VerifyScreen(state: VerifyUiState, onAction: (VerifyAction) -> Unit, modifier: Modifier = Modifier) {
    val spacing = HashLensTheme.spacing
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
        ) {
            VerifyTopBar(onBack = { onAction(VerifyAction.Back) })
            Spacer(Modifier.height(spacing.xl))
            when (state) {
                VerifyUiState.Idle -> Intro(onChoose = { onAction(VerifyAction.ChooseImage) })
                is VerifyUiState.Analyzing -> Analysis(state)
                VerifyUiState.UnsupportedFile -> ErrorState(
                    icon = Icons.Outlined.BrokenImage,
                    title = stringResource(R.string.verification_unsupported_title),
                    message = stringResource(R.string.verification_unsupported_message),
                    actionLabel = stringResource(R.string.action_choose_another_image),
                    onAction = { onAction(VerifyAction.ChooseImage) }
                )
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
internal fun VerifyTopBar(title: String = stringResource(R.string.verify_title), onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HashLensTheme.sizes.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun Intro(onChoose: () -> Unit) {
    val spacing = HashLensTheme.spacing
    Text(
        stringResource(R.string.verify_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(spacing.xxl))
    PrimaryButton(stringResource(R.string.action_choose_image), onClick = onChoose, icon = Icons.Outlined.PhotoLibrary)
    Spacer(Modifier.height(spacing.sm))
    Text(
        stringResource(R.string.accepted_formats),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Analysis(state: VerifyUiState.Analyzing) {
    Column(Modifier.fillMaxWidth()) {
        if (state.thumbnail != null) {
            ExaminedImage(
                image = state.thumbnail,
                markColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(HashLensTheme.spacing.xl))
        }
        StepProgress(state.step.steps())
    }
}

/** Imagem sob exame, com as cantoneiras (direção visual §5) na cor de [markColor]. */
@Composable
internal fun ExaminedImage(image: ImageBitmap, markColor: Color, modifier: Modifier = Modifier) {
    val sizes = HashLensTheme.sizes
    Box(
        modifier
            .cornerMarks(markColor, sizes.cornerMarkStroke, sizes.cornerMarkLength)
            .padding(HashLensTheme.spacing.sm + sizes.cornerMarkStroke)
    ) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.verify_image_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.sizeIn(maxWidth = sizes.thumbnail, maxHeight = sizes.thumbnail)
        )
    }
}

private fun AnalysisStep.steps(): List<ProgressStep> = when (this) {
    AnalysisStep.Reading -> listOf(
        ProgressStep(R.string.step_reading_image, StepState.Active),
        ProgressStep(R.string.step_querying_records, StepState.Waiting)
    )
    AnalysisStep.Querying -> listOf(
        ProgressStep(R.string.step_reading_image, StepState.Done),
        ProgressStep(R.string.step_querying_records, StepState.Active)
    )
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun VerifyIdlePreview() = HashLensTheme {
    VerifyScreen(VerifySamples.idle, onAction = {})
}

@PreviewLightDark
@Composable
private fun VerifyReadingPreview() = HashLensTheme {
    VerifyScreen(VerifySamples.reading, onAction = {})
}

@Preview
@Composable
private fun VerifyQueryingPreview() = HashLensTheme {
    VerifyScreen(VerifySamples.querying, onAction = {})
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun VerifyUnsupportedPreview() = HashLensTheme {
    VerifyScreen(VerifySamples.unsupported, onAction = {})
}

/** Estados de Verificar imagem (ux.md §4.7), compartilhados por previews e screenshot tests. */
object VerifySamples {
    val idle: VerifyUiState = VerifyUiState.Idle
    val reading: VerifyUiState get() = VerifyUiState.Analyzing(AnalysisStep.Reading, SampleData.Photo.landscape())
    val querying: VerifyUiState get() = VerifyUiState.Analyzing(AnalysisStep.Querying, SampleData.Photo.landscape())
    val unsupported: VerifyUiState = VerifyUiState.UnsupportedFile
}
