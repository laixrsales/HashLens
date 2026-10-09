package br.ufv.hashlens.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Paleta da direção visual aprovada (docs/design/direcao-visual.md §2). "A cor é informação": a
// interface é neutra e a cor só aparece nos status. Contraste conferido em ThemeContrastTest.

// Base neutra: claro
internal val PaperLight = Color(0xFFF6F4EF)
internal val Paper2Light = Color(0xFFECE9E2)
internal val GraphiteLight = Color(0xFF1C1D1F)
internal val Gray18Light = Color(0xFF64656A)
internal val OutlineLight = Color(0xFF8A8B8F)

// Base neutra: escuro
internal val PaperDark = Color(0xFF141516)
internal val Paper2Dark = Color(0xFF1F2123)
internal val GraphiteDark = Color(0xFFECE9E2)
internal val Gray18Dark = Color(0xFFA6A7AB)
internal val OutlineDark = Color(0xFF76777B)

/** Cor de um status (ícone, título, destaques) e o fundo do cabeçalho e dos selos. */
@Immutable
data class StatusColor(val content: Color, val container: Color)

/** Cores de status (direção visual §2.2). Nunca aparecem sem o ícone e a palavra do status (ux.md §5). */
@Immutable
data class StatusColors(
    /** Registrada – original; também "Foto registrada". Verde folha. */
    val original: StatusColor,
    /** Registrada – versão alterada. Cianótipo. */
    val edited: StatusColor,
    /** Correspondência visual. Violeta. */
    val visualMatch: StatusColor,
    /** Registro adulterado. Vermelho óxido. */
    val tampered: StatusColor,
    /** Não registrada: neutra, porque não significa que a imagem seja falsa. Cinza 18%. */
    val notRegistered: StatusColor,
    /** Registrando… Âmbar revelação. */
    val pending: StatusColor,
    /** Não registrada – tentar de novo. Vermelho óxido. */
    val failed: StatusColor
) {
    val all: List<StatusColor> get() = listOf(original, edited, visualMatch, tampered, notRegistered, pending, failed)
}

private val OxideRedLight = StatusColor(content = Color(0xFFAE2F29), container = Color(0xFFF8DFDC))
private val OxideRedDark = StatusColor(content = Color(0xFFFF9F95), container = Color(0xFF47201C))

internal val LightStatusColors = StatusColors(
    original = StatusColor(content = Color(0xFF2E6A3A), container = Color(0xFFDCEBDC)),
    edited = StatusColor(content = Color(0xFF1E4F86), container = Color(0xFFDCE6F3)),
    visualMatch = StatusColor(content = Color(0xFF6B4596), container = Color(0xFFECE2F6)),
    tampered = OxideRedLight,
    notRegistered = StatusColor(content = Color(0xFF5F6065), container = Color(0xFFE6E4DF)),
    pending = StatusColor(content = Color(0xFF875200), container = Color(0xFFF6E6CC)),
    failed = OxideRedLight
)

internal val DarkStatusColors = StatusColors(
    original = StatusColor(content = Color(0xFF8FCB97), container = Color(0xFF1D3622)),
    edited = StatusColor(content = Color(0xFFA3C3EE), container = Color(0xFF192C43)),
    visualMatch = StatusColor(content = Color(0xFFC9AEEC), container = Color(0xFF31254A)),
    tampered = OxideRedDark,
    notRegistered = StatusColor(content = Color(0xFFB4B5B9), container = Color(0xFF2B2C2F)),
    pending = StatusColor(content = Color(0xFFEDB766), container = Color(0xFF3A2A0F)),
    failed = OxideRedDark
)

/** Ação primária em Grafite (botão cheio); sem cor de marca. Erro do M3 = Vermelho óxido. */
internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = GraphiteLight,
    onPrimary = PaperLight,
    primaryContainer = Paper2Light,
    onPrimaryContainer = GraphiteLight,
    inversePrimary = GraphiteDark,
    secondary = Gray18Light,
    onSecondary = PaperLight,
    secondaryContainer = Paper2Light,
    onSecondaryContainer = GraphiteLight,
    tertiary = Gray18Light,
    onTertiary = PaperLight,
    tertiaryContainer = Paper2Light,
    onTertiaryContainer = GraphiteLight,
    background = PaperLight,
    onBackground = GraphiteLight,
    surface = PaperLight,
    onSurface = GraphiteLight,
    surfaceVariant = Paper2Light,
    onSurfaceVariant = Gray18Light,
    surfaceTint = GraphiteLight,
    inverseSurface = GraphiteLight,
    inverseOnSurface = PaperLight,
    error = OxideRedLight.content,
    onError = PaperLight,
    errorContainer = OxideRedLight.container,
    onErrorContainer = OxideRedLight.content,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    scrim = Color.Black,
    surfaceBright = PaperLight,
    surfaceDim = Color(0xFFE7E4DC),
    surfaceContainerLowest = Color(0xFFFBFAF7),
    surfaceContainerLow = Color(0xFFF1EEE8),
    surfaceContainer = Paper2Light,
    // Containers altos limitados pelo texto secundário (Cinza 18%) a ≥ 4,5:1
    surfaceContainerHigh = Color(0xFFE9E6DF),
    surfaceContainerHighest = Color(0xFFE7E4DC)
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = GraphiteDark,
    onPrimary = PaperDark,
    primaryContainer = Paper2Dark,
    onPrimaryContainer = GraphiteDark,
    inversePrimary = GraphiteLight,
    secondary = Gray18Dark,
    onSecondary = PaperDark,
    secondaryContainer = Paper2Dark,
    onSecondaryContainer = GraphiteDark,
    tertiary = Gray18Dark,
    onTertiary = PaperDark,
    tertiaryContainer = Paper2Dark,
    onTertiaryContainer = GraphiteDark,
    background = PaperDark,
    onBackground = GraphiteDark,
    surface = PaperDark,
    onSurface = GraphiteDark,
    surfaceVariant = Paper2Dark,
    onSurfaceVariant = Gray18Dark,
    surfaceTint = GraphiteDark,
    inverseSurface = GraphiteDark,
    inverseOnSurface = PaperDark,
    error = OxideRedDark.content,
    onError = PaperDark,
    errorContainer = OxideRedDark.container,
    onErrorContainer = OxideRedDark.content,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    scrim = Color.Black,
    surfaceBright = Color(0xFF38393B),
    surfaceDim = PaperDark,
    surfaceContainerLowest = Color(0xFF0F1011),
    surfaceContainerLow = Color(0xFF1A1B1D),
    surfaceContainer = Paper2Dark,
    surfaceContainerHigh = Color(0xFF292B2D),
    surfaceContainerHighest = Color(0xFF333537)
)
