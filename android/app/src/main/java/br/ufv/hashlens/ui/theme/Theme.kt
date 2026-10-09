package br.ufv.hashlens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalSizes = staticCompositionLocalOf { Sizes() }
private val LocalExtendedTypography = staticCompositionLocalOf { HashLensExtendedTypography }

/**
 * Tema do HashLens (docs/design/direcao-visual.md), claro e escuro. Cor dinâmica (Material You)
 * desligada: as cores de status não podem variar com o papel de parede.
 *
 * Cores, tipografia e formas do Material 3 vêm de [MaterialTheme]; status, espaçamentos, medidas e
 * estilos extras, de [HashLensTheme]. Telas e componentes nunca usam valores literais.
 */
@Composable
fun HashLensTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalSpacing provides Spacing(),
        LocalSizes provides Sizes(),
        LocalExtendedTypography provides HashLensExtendedTypography
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = HashLensTypography,
            shapes = HashLensShapes,
            content = content
        )
    }
}

/** Tokens além do Material 3. Uso: `HashLensTheme.statusColors.original.content`. */
object HashLensTheme {
    val statusColors: StatusColors
        @Composable @ReadOnlyComposable
        get() = LocalStatusColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    val sizes: Sizes
        @Composable @ReadOnlyComposable
        get() = LocalSizes.current

    val typography: ExtendedTypography
        @Composable @ReadOnlyComposable
        get() = LocalExtendedTypography.current
}
