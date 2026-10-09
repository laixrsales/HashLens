package br.ufv.hashlens.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.ufv.hashlens.R

// Tipografia da direção visual (§3): Atkinson Hyperlegible Next na interface e Mono em endereços,
// identificadores e números da régua. Instâncias estáticas em res/font (android/third_party/fonts).

val AtkinsonNext = FontFamily(
    Font(R.font.atkinson_next_regular, FontWeight.Normal),
    Font(R.font.atkinson_next_medium, FontWeight.Medium),
    Font(R.font.atkinson_next_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_next_bold, FontWeight.Bold)
)

val AtkinsonMono = FontFamily(
    Font(R.font.atkinson_mono_regular, FontWeight.Normal),
    Font(R.font.atkinson_mono_semibold, FontWeight.SemiBold)
)

private fun next(weight: FontWeight, size: Int, lineHeight: Int, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = AtkinsonNext,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

internal val HashLensTypography = Typography(
    displayLarge = next(FontWeight.Bold, size = 48, lineHeight = 56),
    displayMedium = next(FontWeight.Bold, size = 40, lineHeight = 48),
    displaySmall = next(FontWeight.Bold, size = 34, lineHeight = 42),
    headlineLarge = next(FontWeight.Bold, size = 32, lineHeight = 40),
    headlineMedium = next(FontWeight.Bold, size = 28, lineHeight = 36),
    headlineSmall = next(FontWeight.SemiBold, size = 24, lineHeight = 32),
    titleLarge = next(FontWeight.SemiBold, size = 20, lineHeight = 28),
    titleMedium = next(FontWeight.SemiBold, size = 16, lineHeight = 24),
    titleSmall = next(FontWeight.SemiBold, size = 14, lineHeight = 20),
    bodyLarge = next(FontWeight.Normal, size = 17, lineHeight = 26),
    bodyMedium = next(FontWeight.Normal, size = 15, lineHeight = 22),
    bodySmall = next(FontWeight.Normal, size = 13, lineHeight = 18),
    labelLarge = next(FontWeight.SemiBold, size = 15, lineHeight = 20, letterSpacing = 0.1),
    labelMedium = next(FontWeight.SemiBold, size = 13, lineHeight = 16, letterSpacing = 0.1),
    labelSmall = next(FontWeight.Medium, size = 11, lineHeight = 16, letterSpacing = 0.2)
)

/** Estilos fora da escala do Material 3. */
@Immutable
data class ExtendedTypography(
    /** Endereço, identificadores e transação nos "Detalhes técnicos". */
    val technical: TextStyle,
    /** Números da régua de alteração (dígitos alinhados). */
    val scale: TextStyle
)

internal val HashLensExtendedTypography = ExtendedTypography(
    technical = TextStyle(
        fontFamily = AtkinsonMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    scale = TextStyle(
        fontFamily = AtkinsonMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
