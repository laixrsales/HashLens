package br.ufv.hashlens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import br.ufv.hashlens.ui.theme.HashLensTheme

/** Empilha os estados de um componente nos previews e screenshot tests. */
@Composable
internal fun SampleColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HashLensTheme.spacing.xl)) { content() }
}
