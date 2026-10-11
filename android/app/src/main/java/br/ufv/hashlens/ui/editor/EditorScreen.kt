package br.ufv.hashlens.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.ui.capture.ProcessingLabel
import br.ufv.hashlens.ui.common.EditOperationFormat
import br.ufv.hashlens.ui.components.ErrorState
import br.ufv.hashlens.ui.components.MessageState
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.SecondaryButton
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme

/**
 * Editor (ux.md §4.6): a foto em tela cheia, sempre no escuro como a câmera, com a faixa de filtros,
 * o ajuste do filtro ativo e "Salvar e registrar" na zona do polegar. Desfazer e refazer ficam no
 * topo; tocar e segurar a foto mostra a original. Antes de registrar, o aviso de permanência.
 *
 * Imagem não elegível volta ao tema do app, porque não há foto a mostrar.
 */
@Composable
fun EditorScreen(state: EditorUiState, onAction: (EditorAction) -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        EditorUiState.Loading -> DarkFrame(modifier) {
            Box(Modifier.fillMaxSize()) {
                CloseButton(onAction, Modifier.align(Alignment.TopStart))
                Box(Modifier.align(Alignment.Center)) { ProcessingLabel(stringResource(R.string.editor_loading)) }
            }
        }
        is EditorUiState.NotEligible -> NotEligible(state.reason, onAction, modifier)
        is EditorUiState.Editing -> DarkFrame(
            modifier,
            overlay = { if (state.confirmingSave) PermanenceSheet(state.operations, onAction) }
        ) { Editing(state, onAction) }
    }
}

/**
 * Fundo escuro e tema escuro nos dois modos do sistema: a foto é o assunto (como na câmera).
 * [overlay] cobre a tela inteira, sem o recuo do conteúdo (aviso de permanência).
 */
@Composable
private fun DarkFrame(modifier: Modifier, overlay: @Composable () -> Unit = {}, content: @Composable () -> Unit) =
    HashLensTheme(darkTheme = true) {
        Surface(modifier.fillMaxSize(), color = HashLensTheme.cameraColors.background) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(HashLensTheme.spacing.lg)) { content() }
            overlay()
        }
    }

@Composable
private fun Editing(state: EditorUiState.Editing, onAction: (EditorAction) -> Unit) {
    val spacing = HashLensTheme.spacing
    val controlsEnabled = !state.processing
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(state, onAction)
            if (state.originalAuthor != null) {
                Spacer(Modifier.height(spacing.sm))
                ThirdPartyBanner(state.originalAuthor, Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(spacing.md))
            Photo(state, onAction, Modifier.weight(1f))
            Spacer(Modifier.height(spacing.md))
            val draft = state.draft
            if (draft != null) {
                AdjustmentPanel(draft, enabled = controlsEnabled, onAction = onAction)
                Spacer(Modifier.height(spacing.sm))
            }
            ToolStrip(state.activeTool, enabled = controlsEnabled && draft == null, onAction = onAction)
            Spacer(Modifier.height(spacing.lg))
            PrimaryButton(
                stringResource(R.string.action_save_and_register),
                onClick = { onAction(EditorAction.Save) },
                enabled = state.canSave
            )
        }
    }
}

/**
 * A foto (prévia ou original), com a moldura do recorte quando ele está em ajuste. Tocar e segurar
 * mostra a original; no leitor de tela, a mesma comparação é uma ação da foto.
 */
@Composable
private fun Photo(state: EditorUiState.Editing, onAction: (EditorAction) -> Unit, modifier: Modifier) {
    val compareLabel = stringResource(R.string.editor_compare_action)
    val image = if (state.showingOriginal) state.original else state.preview
    val description = stringResource(
        if (state.showingOriginal) R.string.editor_original_description else R.string.editor_image_description
    )
    // A caixa interna tem a proporção da foto: etiquetas e moldura do recorte ficam sobre a própria foto
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(image.width.toFloat() / image.height), contentAlignment = Alignment.Center) {
            Image(
                bitmap = image,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.processing) {
                        if (state.processing) return@pointerInput
                        detectTapGestures(onPress = {
                            onAction(EditorAction.Compare(showingOriginal = true))
                            tryAwaitRelease()
                            onAction(EditorAction.Compare(showingOriginal = false))
                        })
                    }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(compareLabel) {
                                onAction(EditorAction.Compare(showingOriginal = !state.showingOriginal))
                                true
                            }
                        )
                    }
            )
            val crop = state.draft as? EditOperation.Crop
            if (crop != null && !state.showingOriginal) {
                CropFrame(
                    crop = crop,
                    imageWidth = image.width,
                    imageHeight = image.height,
                    onChange = { onAction(EditorAction.UpdateDraft(it)) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            when {
                state.processing -> ProcessingLabel(stringResource(R.string.editor_processing))
                state.showingOriginal -> PhotoLabel(
                    stringResource(R.string.editor_showing_original),
                    Modifier.align(Alignment.TopCenter)
                )
                state.operations.isNotEmpty() && state.draft == null ->
                    PhotoLabel(stringResource(R.string.editor_compare_hint), Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Etiqueta curta sobre a foto, num véu para ler em qualquer fundo. */
@Composable
private fun PhotoLabel(text: String, modifier: Modifier) {
    val camera = HashLensTheme.cameraColors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = camera.content,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(HashLensTheme.spacing.sm)
            .background(camera.scrim, MaterialTheme.shapes.medium)
            .padding(horizontal = HashLensTheme.spacing.md, vertical = HashLensTheme.spacing.xs)
    )
}

/**
 * Aviso de permanência (ux.md §4.6, hashlens-ui "reversível antes de definitivo"): o que vai ser
 * registrado e que não poderá ser apagado, antes de abrir a carteira. Folha presa embaixo, sobre a
 * tela escurecida, no mesmo tema escuro do Editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermanenceSheet(operations: List<EditOperation>, onAction: (EditorAction) -> Unit) {
    val spacing = HashLensTheme.spacing
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = BottomSheetDefaults.ScrimColor.alpha))
            .pointerInput(Unit) { detectTapGestures { onAction(EditorAction.DismissSave) } }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Toques na folha não fecham o aviso
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = spacing.lg, vertical = spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                Text(
                    stringResource(R.string.editor_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.editor_confirm_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(
                        R.string.editor_confirm_operations,
                        EditOperationFormat.list(LocalResources.current, operations)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(spacing.sm))
                PrimaryButton(stringResource(R.string.action_save_and_register), onClick = {
                    onAction(EditorAction.ConfirmSave)
                })
                SecondaryButton(stringResource(R.string.action_keep_editing), onClick = {
                    onAction(EditorAction.DismissSave)
                })
            }
        }
    }
}

/** Imagem que não pode ser editada: o que aconteceu e o que fazer, no tema do app. */
@Composable
private fun NotEligible(reason: IneligibleReason, onAction: (EditorAction) -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(HashLensTheme.spacing.lg)
        ) {
            CloseButton(onAction)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val back = stringResource(R.string.action_back)
                when (reason) {
                    // Não é erro: o registro só não terminou. Ícone e cor de "Registrando…"
                    IneligibleReason.Pending -> MessageState(
                        icon = Icons.Outlined.Schedule,
                        iconTint = HashLensTheme.statusColors.pending.content,
                        title = stringResource(R.string.editor_pending_title),
                        message = stringResource(R.string.editor_pending_message)
                    ) { SecondaryButton(back, onClick = { onAction(EditorAction.Close) }) }
                    IneligibleReason.RegistrationFailed -> ErrorState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.editor_failed_title),
                        message = stringResource(R.string.editor_failed_message),
                        actionLabel = back,
                        onAction = { onAction(EditorAction.Close) }
                    )
                    IneligibleReason.NotRegistered -> ErrorState(
                        icon = Icons.Outlined.BrokenImage,
                        title = stringResource(R.string.editor_not_registered_title),
                        message = stringResource(R.string.editor_not_registered_message),
                        actionLabel = back,
                        onAction = { onAction(EditorAction.Close) }
                    )
                    IneligibleReason.Tampered -> ErrorState(
                        icon = Icons.Outlined.GppBad,
                        title = stringResource(R.string.editor_tampered_title),
                        message = stringResource(R.string.editor_tampered_message),
                        actionLabel = back,
                        onAction = { onAction(EditorAction.Close) }
                    )
                    IneligibleReason.ImportFailed -> ErrorState(
                        icon = Icons.Outlined.CloudOff,
                        title = stringResource(R.string.editor_import_failed_title),
                        message = stringResource(R.string.editor_import_failed_message),
                        actionLabel = stringResource(R.string.action_try_again),
                        onAction = { onAction(EditorAction.RetryImport) }
                    )
                    IneligibleReason.FileUnavailable -> ErrorState(
                        icon = Icons.Outlined.HideImage,
                        title = stringResource(R.string.editor_unavailable_title),
                        message = stringResource(R.string.editor_unavailable_message),
                        actionLabel = back,
                        onAction = { onAction(EditorAction.Close) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CloseButton(onAction: (EditorAction) -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = { onAction(EditorAction.Close) }, modifier = modifier) {
        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun EditorEditingPreview() = HashLensTheme { EditorScreen(EditorSamples.editing, onAction = {}) }

@Preview
@Composable
private fun EditorAdjustingPreview() = HashLensTheme { EditorScreen(EditorSamples.adjustingBrightness, onAction = {}) }

@Preview
@Composable
private fun EditorCropPreview() = HashLensTheme { EditorScreen(EditorSamples.cropping, onAction = {}) }

@Preview
@Composable
private fun EditorNoChangesPreview() = HashLensTheme { EditorScreen(EditorSamples.noChanges, onAction = {}) }

@Preview
@Composable
private fun EditorThirdPartyPreview() = HashLensTheme { EditorScreen(EditorSamples.thirdParty, onAction = {}) }

@Preview
@Composable
private fun EditorComparingPreview() = HashLensTheme { EditorScreen(EditorSamples.comparing, onAction = {}) }

@PreviewLightDark
@PreviewFontScale
@Composable
private fun EditorConfirmPreview() = HashLensTheme { EditorScreen(EditorSamples.confirming, onAction = {}) }

@Preview
@Composable
private fun EditorProcessingPreview() = HashLensTheme { EditorScreen(EditorSamples.processing, onAction = {}) }

@Preview
@Composable
private fun EditorLoadingPreview() = HashLensTheme { EditorScreen(EditorSamples.loading, onAction = {}) }

@PreviewLightDark
@PreviewFontScale
@Composable
private fun EditorPendingPreview() = HashLensTheme { EditorScreen(EditorSamples.pending, onAction = {}) }

@Preview
@Composable
private fun EditorRegistrationFailedPreview() = HashLensTheme {
    EditorScreen(EditorSamples.registrationFailed, onAction = {})
}

@Preview
@Composable
private fun EditorNotRegisteredPreview() = HashLensTheme { EditorScreen(EditorSamples.notRegistered, onAction = {}) }

@Preview
@Composable
private fun EditorTamperedPreview() = HashLensTheme { EditorScreen(EditorSamples.tampered, onAction = {}) }

@Preview
@Composable
private fun EditorImportFailedPreview() = HashLensTheme { EditorScreen(EditorSamples.importFailed, onAction = {}) }

@Preview
@Composable
private fun EditorUnavailablePreview() = HashLensTheme { EditorScreen(EditorSamples.unavailable, onAction = {}) }

/** Estados do Editor (ux.md §4.6), compartilhados por previews e screenshot tests. */
object EditorSamples {
    private val operations = listOf(EditOperation.Grayscale, EditOperation.Brightness(30))

    private fun sample(edited: Boolean = true) = EditorUiState.Editing(
        preview = if (edited) SampleData.Photo.grayscale() else SampleData.Photo.landscape(),
        original = SampleData.Photo.landscape(),
        operations = if (edited) operations else emptyList(),
        canUndo = edited
    )

    val loading: EditorUiState = EditorUiState.Loading
    val noChanges: EditorUiState get() = sample(edited = false)
    val editing: EditorUiState get() = sample()
    val adjustingBrightness: EditorUiState
        get() = sample().copy(activeTool = EditorTool.Brightness, draft = EditOperation.Brightness(30))
    val cropping: EditorUiState
        get() = sample().copy(activeTool = EditorTool.Crop, draft = EditOperation.Crop(0.1f, 0.1f, 0.8f, 0.75f))
    val comparing: EditorUiState get() = sample().copy(showingOriginal = true)
    val thirdParty: EditorUiState get() = sample().copy(originalAuthor = SampleData.Wallets.OTHER)
    val confirming: EditorUiState get() = sample().copy(confirmingSave = true)
    val processing: EditorUiState get() = sample().copy(processing = true)
    val pending: EditorUiState = EditorUiState.NotEligible(IneligibleReason.Pending)
    val registrationFailed: EditorUiState = EditorUiState.NotEligible(IneligibleReason.RegistrationFailed)
    val notRegistered: EditorUiState = EditorUiState.NotEligible(IneligibleReason.NotRegistered)
    val unavailable: EditorUiState = EditorUiState.NotEligible(IneligibleReason.FileUnavailable)
    val tampered: EditorUiState = EditorUiState.NotEligible(IneligibleReason.Tampered)
    val importFailed: EditorUiState = EditorUiState.NotEligible(IneligibleReason.ImportFailed)
}
