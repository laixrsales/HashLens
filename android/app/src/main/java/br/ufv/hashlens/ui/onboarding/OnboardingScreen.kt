package br.ufv.hashlens.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.components.PrimaryButton
import br.ufv.hashlens.ui.components.cornerMarks
import br.ufv.hashlens.ui.theme.HashLensTheme

/**
 * "Como funciona" (ux.md §4.1; FR-037, FR-040): uma tela, pulável, no primeiro uso. Explica
 * registrar, verificar e editar (critérios e filtros), o que o registro prova, a rede de testes e
 * os formatos aceitos. Estado único.
 */
@Composable
fun OnboardingScreen(onStart: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = HashLensTheme.spacing
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.xs)
            ) {
                TextButton(
                    onClick = onSkip,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text(stringResource(R.string.action_skip), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        bottomBar = {
            PrimaryButton(
                text = stringResource(R.string.action_start),
                onClick = onStart,
                modifier = Modifier.padding(spacing.lg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
        ) {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(spacing.md))
            Text(
                stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(spacing.xxl))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
                Idea(Icons.Outlined.PhotoCamera, R.string.onboarding_register_title, R.string.onboarding_register_text)
                Idea(Icons.Outlined.Search, R.string.onboarding_verify_title, R.string.onboarding_verify_text)
                Idea(Icons.Outlined.EditNote, R.string.onboarding_edit_title, R.string.onboarding_edit_text)
            }
            Spacer(Modifier.height(spacing.xl))
            Footnotes()
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

/** O que o registro prova (e não prova), a rede de testes e os formatos aceitos. */
@Composable
private fun Footnotes() {
    val spacing = HashLensTheme.spacing
    Column {
        HorizontalDivider(thickness = HashLensTheme.sizes.divider, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(spacing.lg))
        Text(
            stringResource(R.string.onboarding_limits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(spacing.lg))
        Note(Icons.Outlined.Science, R.string.onboarding_test_network)
        Spacer(Modifier.height(spacing.sm))
        Note(Icons.Outlined.Image, R.string.onboarding_formats)
    }
}

/** Uma das três ideias: ilustração simples (ícone entre cantoneiras), título e frase. */
@Composable
private fun Idea(icon: ImageVector, @StringRes title: Int, @StringRes text: Int) {
    val spacing = HashLensTheme.spacing
    val sizes = HashLensTheme.sizes
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Box(
            modifier = Modifier
                .size(sizes.primaryButtonHeight)
                .cornerMarks(MaterialTheme.colorScheme.onSurface, sizes.cornerMarkStroke, sizes.cornerMarkLength / 2),
            contentAlignment = Alignment.Center
        ) {
            // Decorativo: o título da ideia diz o mesmo
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Aviso de uma linha com ícone (rede de testes, formatos). */
@Composable
private fun Note(icon: ImageVector, @StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.sm)) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(HashLensTheme.sizes.icon)
        )
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun OnboardingPreview() = HashLensTheme {
    OnboardingScreen(onStart = {}, onSkip = {})
}
