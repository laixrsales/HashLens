package br.ufv.hashlens.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.common.motionDuration
import br.ufv.hashlens.ui.preview.ComponentPreview
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme
import br.ufv.hashlens.ui.theme.Motion

/** Situação de uma etapa. A cor acompanha o vocabulário de status: âmbar em andamento, vermelho na falha. */
enum class StepState(@StringRes val description: Int) {
    Done(R.string.step_state_done),
    Active(R.string.step_state_active),
    Waiting(R.string.step_state_waiting),
    Failed(R.string.step_state_failed)
}

/** Uma etapa de uma espera longa; [detail] diz o que está acontecendo ou o que deu errado. */
data class ProgressStep(@StringRes val label: Int, val state: StepState, @StringRes val detail: Int? = null)

/**
 * Etapas de uma espera longa (FR-035): Acompanhamento do registro e análise da verificação.
 * Nunca um indicador sem texto: cada etapa tem nome, e a ativa ou com falha tem uma frase.
 */
@Composable
fun StepProgress(steps: List<ProgressStep>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            StepRow(step, isLast = index == steps.lastIndex)
        }
    }
}

@Composable
private fun StepRow(step: ProgressStep, isLast: Boolean) {
    val spacing = HashLensTheme.spacing
    val sizes = HashLensTheme.sizes
    val label = stringResource(step.label)
    val detail = step.detail?.let { stringResource(it) }
    val state = stringResource(step.state.description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            // TalkBack lê "Enviado, em andamento. Aguardando a confirmação da rede."
            .clearAndSetSemantics { contentDescription = listOfNotNull("$label, $state.", detail).joinToString(" ") },
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Crossfade(
                targetState = step.state,
                animationSpec = tween(motionDuration(Motion.STEP_MS)),
                label = "etapa"
            ) {
                StepIcon(it)
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(sizes.divider)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
        }
        Column(
            modifier = Modifier.padding(bottom = if (isLast) spacing.xs else spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (step.state == StepState.Waiting) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (detail != null) {
                Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = stepColor(step.state))
            }
        }
    }
}

@Composable
private fun StepIcon(state: StepState) {
    val modifier = Modifier.size(HashLensTheme.sizes.icon)
    when (state) {
        StepState.Done -> Icon(Icons.Outlined.CheckCircle, null, modifier, tint = MaterialTheme.colorScheme.onSurface)
        StepState.Active -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(HashLensTheme.sizes.smallIcon),
                color = HashLensTheme.statusColors.pending.content,
                strokeWidth = HashLensTheme.sizes.cornerMarkStroke
            )
        }
        StepState.Waiting -> Icon(
            Icons.Outlined.RadioButtonUnchecked,
            null,
            modifier,
            tint = MaterialTheme.colorScheme.outline
        )
        StepState.Failed -> Icon(
            Icons.Outlined.ErrorOutline,
            null,
            modifier,
            tint = HashLensTheme.statusColors.failed.content
        )
    }
}

@Composable
private fun stepColor(state: StepState): Color = when (state) {
    StepState.Active -> HashLensTheme.statusColors.pending.content
    StepState.Failed -> HashLensTheme.statusColors.failed.content
    StepState.Done, StepState.Waiting -> MaterialTheme.colorScheme.onSurfaceVariant
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun StepProgressRegistrationPreview() = ComponentPreview {
    StepProgressSamples.Registration()
}

@Preview
@Composable
private fun StepProgressFailuresPreview() = ComponentPreview {
    StepProgressSamples.Failures()
}

@Preview
@Composable
private fun StepProgressVerificationPreview() = ComponentPreview {
    StepProgressSamples.Verification()
}

/** Todos os estados das etapas (ux.md §4.5 e §4.7), compartilhados por previews e screenshot tests. */
object StepProgressSamples {
    @Composable
    fun Registration() = SampleColumn {
        SampleData.Steps.registration.forEach { StepProgress(it) }
    }

    @Composable
    fun Failures() = SampleColumn {
        SampleData.Steps.failures.forEach { StepProgress(it) }
    }

    @Composable
    fun Verification() = SampleColumn {
        SampleData.Steps.verification.forEach { StepProgress(it) }
    }
}
