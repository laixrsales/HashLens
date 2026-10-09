package br.ufv.hashlens.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Raios pequenos, de instrumento e etiqueta (direção visual §4). Botões usam `medium` (8 dp), não pílula.
internal val HashLensShapes = Shapes(
    /** Marcas e cursor da régua. */
    extraSmall = RoundedCornerShape(2.dp),
    /** Selos de status, miniaturas. */
    small = RoundedCornerShape(4.dp),
    /** Botões, campos, chip da carteira, painel técnico. */
    medium = RoundedCornerShape(8.dp),
    /** Cabeçalho de status. */
    large = RoundedCornerShape(12.dp),
    /** Topo dos bottom sheets. */
    extraLarge = RoundedCornerShape(16.dp)
)
