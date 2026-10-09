package br.ufv.hashlens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Tema provisório: esquemas padrão do Material 3, claro e escuro, sem cor dinâmica (as cores de
 * status não podem variar com o papel de parede). Cores, tipografia, formas e espaçamentos chegam
 * com a direção visual aprovada (T033/T034).
 */
@Composable
fun HashLensTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content
    )
}
