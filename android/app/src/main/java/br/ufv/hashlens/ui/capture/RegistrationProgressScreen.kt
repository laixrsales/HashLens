package br.ufv.hashlens.ui.capture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.DateFormat
import br.ufv.hashlens.ui.common.SepoliaEthFormat
import br.ufv.hashlens.ui.common.motionDuration
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.ProgressStep
import br.ufv.hashlens.ui.components.SecondaryButton
import br.ufv.hashlens.ui.components.StatusHeader
import br.ufv.hashlens.ui.components.StepProgress
import br.ufv.hashlens.ui.components.StepState
import br.ufv.hashlens.ui.components.TechnicalDetails
import br.ufv.hashlens.ui.components.cornerMarks
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme
import br.ufv.hashlens.ui.theme.Motion
import java.time.ZoneId

/**
 * Acompanhamento do registro (ux.md §4.5): o status, a foto sob exame e as etapas da espera,
 * deixando claro que a pessoa pode sair da tela (FR-035).
 *
 * Momento marcante (direção visual §6): ao confirmar, a miniatura em tons de cinza revela as cores,
 * as cantoneiras passam do âmbar ao verde, e o aparelho vibra uma vez.
 *
 * @param zone fuso da data de confirmação; os previews fixam um para serem determinísticos.
 */
@Composable
fun RegistrationProgressScreen(
    state: RegistrationProgressUiState,
    onAction: (RegistrationProgressAction) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    val spacing = HashLensTheme.spacing
    val stage = state.stage
    ConfirmationHaptic(stage)
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
        ) {
            TopBar(onClose = { onAction(RegistrationProgressAction.Close) })
            Spacer(Modifier.height(spacing.lg))
            Header(stage, zone)
            if (state.thumbnail != null) {
                Spacer(Modifier.height(spacing.xl))
                Thumbnail(state.thumbnail, stage, Modifier.align(Alignment.CenterHorizontally))
            }
            if (stage !is RegistrationStage.AlreadyRegistered) {
                Spacer(Modifier.height(spacing.xl))
                StepProgress(stage.steps())
            }
            if (stage is RegistrationStage.Confirmed && state.technical.isNotEmpty()) {
                Spacer(Modifier.height(spacing.lg))
                var expanded by rememberSaveable { mutableStateOf(false) }
                TechnicalDetails(
                    items = state.technical,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onOpenUrl = { onAction(RegistrationProgressAction.OpenUrl(it)) }
                )
            }
            Spacer(Modifier.height(spacing.xxl))
            Actions(stage, onAction)
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun TopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HashLensTheme.sizes.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
        }
        Text(
            stringResource(R.string.registration_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun Header(stage: RegistrationStage, zone: ZoneId) {
    when (stage) {
        is RegistrationStage.BindingDevice -> StatusHeader(
            ImageStatus.Pending,
            stringResource(R.string.registration_binding_message)
        )
        is RegistrationStage.AwaitingApproval -> StatusHeader(
            ImageStatus.Pending,
            listOfNotNull(
                stringResource(R.string.registration_awaiting_message),
                stage.estimatedCost?.let {
                    stringResource(R.string.registration_estimated_cost, SepoliaEthFormat.amount(it, roundUp = true))
                }
            ).joinToString(" ")
        )
        RegistrationStage.Pending -> StatusHeader(
            ImageStatus.Pending,
            stringResource(R.string.registration_pending_message)
        )
        is RegistrationStage.Confirmed -> StatusHeader(
            ImageStatus.Original,
            stringResource(R.string.registration_confirmed_message, DateFormat.dateTime(stage.registeredAt, zone)),
            title = stringResource(R.string.status_photo_registered)
        )
        is RegistrationStage.Failed -> StatusHeader(
            ImageStatus.Failed,
            stringResource(
                when (stage.reason) {
                    RegistrationFailure.Rejected, RegistrationFailure.Network -> R.string.registration_failed_message
                    RegistrationFailure.Timeout -> R.string.registration_timeout_message
                    RegistrationFailure.HashMismatch -> R.string.registration_hash_mismatch_message
                }
            )
        )
        is RegistrationStage.InsufficientFunds -> StatusHeader(
            ImageStatus.Failed,
            stringResource(
                R.string.registration_insufficient_message,
                SepoliaEthFormat.amount(stage.cost, roundUp = true),
                SepoliaEthFormat.amount(stage.balance)
            )
        )
        is RegistrationStage.AlreadyRegistered -> StatusHeader(
            ImageStatus.Original,
            stringResource(R.string.registration_already_message),
            title = stringResource(R.string.registration_already_title)
        )
    }
}

/**
 * Foto sob exame, com cantoneiras na cor do status. Em cinza enquanto não confirma; ao confirmar,
 * revela as cores e as cantoneiras passam ao verde (instantâneo com "Remover animações").
 */
@Composable
private fun Thumbnail(image: ImageBitmap, stage: RegistrationStage, modifier: Modifier = Modifier) {
    val sizes = HashLensTheme.sizes
    val revealed = stage is RegistrationStage.Confirmed || stage is RegistrationStage.AlreadyRegistered
    val duration = if (LocalInspectionMode.current) 0 else motionDuration(Motion.REVEAL_MS)
    val saturation by animateFloatAsState(if (revealed) 1f else 0f, tween(duration), label = "revelação")
    val markColor by animateColorAsState(stage.status.color.content, tween(duration), label = "cor das cantoneiras")
    // Mesma distância entre cantoneiras e foto em todos os estados; na revelação só a cor muda
    Box(
        modifier
            .cornerMarks(markColor, sizes.cornerMarkStroke, sizes.cornerMarkLength)
            .padding(HashLensTheme.spacing.sm + sizes.cornerMarkStroke)
    ) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.registration_photo_description),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }),
            modifier = Modifier.sizeIn(maxWidth = sizes.thumbnail, maxHeight = sizes.thumbnail)
        )
    }
}

/** Vibração curta só na passagem para confirmado, não ao abrir um registro já confirmado. */
@Composable
private fun ConfirmationHaptic(stage: RegistrationStage) {
    val haptic = LocalHapticFeedback.current
    var waitedForConfirmation by remember { mutableStateOf(stage !is RegistrationStage.Confirmed) }
    LaunchedEffect(stage is RegistrationStage.Confirmed) {
        if (stage is RegistrationStage.Confirmed && waitedForConfirmation) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            waitedForConfirmation = false
        }
    }
}

@Composable
private fun Actions(stage: RegistrationStage, onAction: (RegistrationProgressAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.md)) {
        val backHome = @Composable {
            SecondaryButton(
                stringResource(R.string.action_back_home),
                onClick = { onAction(RegistrationProgressAction.BackHome) }
            )
        }
        when (stage) {
            is RegistrationStage.BindingDevice, is RegistrationStage.AwaitingApproval, RegistrationStage.Pending ->
                backHome()
            is RegistrationStage.Confirmed ->
                PrimaryButton(stringResource(R.string.action_done), onClick = {
                    onAction(RegistrationProgressAction.Done)
                })
            is RegistrationStage.Failed -> {
                if (stage.reason == RegistrationFailure.HashMismatch) {
                    PrimaryButton(
                        stringResource(R.string.action_take_photo_again),
                        onClick = { onAction(RegistrationProgressAction.RetakePhoto) }
                    )
                } else {
                    PrimaryButton(
                        stringResource(R.string.action_try_again),
                        onClick = { onAction(RegistrationProgressAction.Retry) }
                    )
                }
                backHome()
            }
            is RegistrationStage.InsufficientFunds -> {
                PrimaryButton(
                    stringResource(R.string.action_get_test_balance),
                    onClick = { onAction(RegistrationProgressAction.OpenFaucet) }
                )
                SecondaryButton(
                    stringResource(R.string.action_try_again),
                    onClick = { onAction(RegistrationProgressAction.Retry) }
                )
            }
            is RegistrationStage.AlreadyRegistered -> {
                PrimaryButton(
                    stringResource(R.string.action_view_record),
                    onClick = { onAction(RegistrationProgressAction.ViewRecord(stage.recordId)) }
                )
                backHome()
            }
        }
    }
}

/** Cor das cantoneiras: âmbar na espera, verde ao confirmar, vermelho na falha. */
private val RegistrationStage.status: ImageStatus
    get() = when (this) {
        is RegistrationStage.BindingDevice, is RegistrationStage.AwaitingApproval, RegistrationStage.Pending ->
            ImageStatus.Pending
        is RegistrationStage.Confirmed, is RegistrationStage.AlreadyRegistered -> ImageStatus.Original
        is RegistrationStage.Failed, is RegistrationStage.InsufficientFunds -> ImageStatus.Failed
    }

/** Etapas (Salva na galeria → [Vínculo do aparelho] → Aprovação na carteira → Enviado → Confirmado). */
internal fun RegistrationStage.steps(): List<ProgressStep> = when (this) {
    is RegistrationStage.BindingDevice -> registrationSteps(StepState.Done).let {
        val detail = if (newKey) {
            R.string.step_device_binding_new_key_detail
        } else {
            R.string.step_device_binding_detail
        }
        listOf(it.first(), ProgressStep(R.string.step_device_binding, StepState.Active, detail)) + it.drop(1)
    }
    is RegistrationStage.AwaitingApproval ->
        registrationSteps(StepState.Done, StepState.Active, walletDetail = R.string.step_wallet_detail)
    RegistrationStage.Pending ->
        registrationSteps(StepState.Done, StepState.Done, StepState.Active, sentDetail = R.string.step_sent_detail)
    is RegistrationStage.Confirmed ->
        registrationSteps(StepState.Done, StepState.Done, StepState.Done, StepState.Done)
    is RegistrationStage.InsufficientFunds ->
        registrationSteps(StepState.Done, StepState.Failed, walletDetail = R.string.step_failed_insufficient)
    is RegistrationStage.Failed -> reason.steps()
    is RegistrationStage.AlreadyRegistered -> emptyList()
}

/** A etapa que falhou e a frase que diz por quê. */
private fun RegistrationFailure.steps(): List<ProgressStep> = when (this) {
    RegistrationFailure.Rejected ->
        registrationSteps(StepState.Done, StepState.Failed, walletDetail = R.string.step_failed_rejected)
    RegistrationFailure.Network ->
        registrationSteps(StepState.Done, StepState.Done, StepState.Failed, sentDetail = R.string.step_failed_network)
    RegistrationFailure.Timeout ->
        registrationSteps(StepState.Done, StepState.Done, StepState.Failed, sentDetail = R.string.step_failed_timeout)
    RegistrationFailure.HashMismatch ->
        registrationSteps(StepState.Failed, savedDetail = R.string.step_failed_hash_mismatch)
}

private fun registrationSteps(
    saved: StepState,
    wallet: StepState = StepState.Waiting,
    sent: StepState = StepState.Waiting,
    confirmed: StepState = StepState.Waiting,
    savedDetail: Int? = null,
    walletDetail: Int? = null,
    sentDetail: Int? = null
) = listOf(
    ProgressStep(R.string.step_saved, saved, savedDetail),
    ProgressStep(R.string.step_wallet, wallet, walletDetail),
    ProgressStep(R.string.step_sent, sent, sentDetail),
    ProgressStep(R.string.step_confirmed, confirmed)
)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun RegistrationPendingPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.pending, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationBindingPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.bindingDevice, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationNewKeyPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.newDeviceKey, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationAwaitingPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.awaitingApproval, onAction = {}, zone = SampleData.ZONE)
}

@PreviewLightDark
@Composable
private fun RegistrationConfirmedPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.confirmed, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationRejectedPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.rejected, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationNetworkPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.networkFailure, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationTimeoutPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.timeout, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationHashMismatchPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.hashMismatch, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationInsufficientPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.insufficientFunds, onAction = {}, zone = SampleData.ZONE)
}

@Preview
@Composable
private fun RegistrationAlreadyPreview() = HashLensTheme {
    RegistrationProgressScreen(RegistrationProgressSamples.alreadyRegistered, onAction = {}, zone = SampleData.ZONE)
}

/** Estados do Acompanhamento (ux.md §4.5), compartilhados por previews e screenshot tests. */
object RegistrationProgressSamples {
    private fun state(stage: RegistrationStage) = RegistrationProgressUiState(stage, SampleData.Photo.landscape())

    val bindingDevice get() = state(RegistrationStage.BindingDevice())
    val newDeviceKey get() = state(RegistrationStage.BindingDevice(newKey = true))
    val awaitingApproval get() = state(RegistrationStage.AwaitingApproval(SampleData.Chain.REGISTRATION_COST))
    val pending get() = state(RegistrationStage.Pending)
    val confirmed
        get() = state(RegistrationStage.Confirmed(SampleData.Records.original.timestamp))
            .copy(technical = SampleData.Technical.original)
    val rejected get() = state(RegistrationStage.Failed(RegistrationFailure.Rejected))
    val networkFailure get() = state(RegistrationStage.Failed(RegistrationFailure.Network))
    val timeout get() = state(RegistrationStage.Failed(RegistrationFailure.Timeout))
    val hashMismatch get() = state(RegistrationStage.Failed(RegistrationFailure.HashMismatch))
    val insufficientFunds
        get() = state(
            RegistrationStage.InsufficientFunds(SampleData.Chain.REGISTRATION_COST, SampleData.Chain.BALANCE_LOW)
        )
    val alreadyRegistered get() = state(RegistrationStage.AlreadyRegistered(SampleData.Records.original.id))
}
