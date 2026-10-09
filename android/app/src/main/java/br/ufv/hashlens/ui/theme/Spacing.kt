package br.ufv.hashlens.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Escala de espaçamento em múltiplos de 4 dp (direção visual §4). */
@Immutable
data class Spacing(
    /** Ícone ↔ texto do selo. */
    val xs: Dp = 4.dp,
    /** Entre linhas de um mesmo fato. */
    val sm: Dp = 8.dp,
    /** Entre itens de lista. */
    val md: Dp = 12.dp,
    /** Margem lateral das telas; padding interno. */
    val lg: Dp = 16.dp,
    /** Entre blocos de uma tela. */
    val xl: Dp = 24.dp,
    /** Antes da ação primária; entre seções principais. */
    val xxl: Dp = 32.dp,
    /** Respiro do topo nas telas de resultado. */
    val xxxl: Dp = 48.dp
)

/** Medidas fixas de componentes (direção visual §4 e §5). */
@Immutable
data class Sizes(
    /** Alvo de toque mínimo. */
    val minTouchTarget: Dp = 48.dp,
    /** Altura do botão primário. */
    val primaryButtonHeight: Dp = 56.dp,
    /** Altura de uma linha de lista com título e descrição. */
    val listItemMinHeight: Dp = 56.dp,
    /** Ícone do cabeçalho de status. */
    val statusIcon: Dp = 32.dp,
    /** Ícone de selo e de item de lista. */
    val icon: Dp = 24.dp,
    /** Ícone dentro de selo. */
    val smallIcon: Dp = 16.dp,
    /** Divisores finos, como num laudo. */
    val divider: Dp = 1.dp,
    /** Traço das cantoneiras. */
    val cornerMarkStroke: Dp = 2.dp,
    /** Comprimento de cada braço das cantoneiras. */
    val cornerMarkLength: Dp = 16.dp
)
