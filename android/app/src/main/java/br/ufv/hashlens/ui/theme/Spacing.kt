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
    val cornerMarkLength: Dp = 16.dp,
    /** Disparador da câmera, na zona do polegar. */
    val shutter: Dp = 72.dp,
    /** Lado maior da miniatura da foto no Acompanhamento do registro. */
    val thumbnail: Dp = 200.dp,
    /** Largura mínima de cada ferramenta na faixa de filtros do Editor. */
    val toolButtonWidth: Dp = 72.dp,
    /** Miniatura de uma versão no Histórico da imagem. */
    val genealogyThumbnail: Dp = 64.dp,
    /** Largura da trilha da linha do tempo do Histórico. */
    val timelineRail: Dp = 24.dp,
    /** Marcador de cada passo da linha do tempo; o da imagem consultada é maior. */
    val timelineMarker: Dp = 10.dp,
    val timelineMarkerLarge: Dp = 14.dp,
    /** Altura do marcador a partir do topo do passo até o selo de status ser medido. */
    val timelineMarkerOffset: Dp = 12.dp,
    /** Largura mínima de cada foto na grade do acervo: duas colunas num telefone. */
    val libraryCellMinWidth: Dp = 150.dp
)
