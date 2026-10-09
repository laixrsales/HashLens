package br.ufv.hashlens.ui.theme

/** Durações das transições (direção visual §6), em ms. Todas viram 0 com "Remover animações". */
object Motion {
    /** Navegação entre telas (deslizar + fade). */
    const val NAVIGATION_MS = 250

    /** Expandir e recolher "Detalhes técnicos". */
    const val EXPAND_MS = 200

    /** Troca de ícone de uma etapa concluída. */
    const val STEP_MS = 150

    /** Cursor da régua até o valor, só na primeira exibição. */
    const val METER_MS = 300

    /** Revelação da miniatura ao confirmar o registro (momento marcante). */
    const val REVEAL_MS = 600
}
