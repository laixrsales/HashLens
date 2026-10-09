package br.ufv.hashlens.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.ufv.hashlens.ui.theme.HashLensTheme

/** Moldura dos `@Preview` de componentes: tema do app (claro/escuro conforme o preview) e fundo Papel. */
@Composable
fun ComponentPreview(content: @Composable () -> Unit) {
    HashLensTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(HashLensTheme.spacing.lg)) { content() }
        }
    }
}
