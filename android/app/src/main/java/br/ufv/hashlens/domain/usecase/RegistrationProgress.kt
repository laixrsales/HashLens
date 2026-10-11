package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.data.chain.TxCost

/**
 * Etapas de um registro em andamento, avisadas enquanto o caso de uso ainda não terminou (a
 * carteira pode ficar aberta por minutos). A tela de Acompanhamento mostra cada uma (ux.md §4.5).
 */
sealed interface RegistrationProgress {
    /** Foto gravada na galeria, conferida e no acervo como [localId]. */
    data class Saved(val localId: Long) : RegistrationProgress

    /**
     * A carteira vai abrir para vincular este aparelho (research R4).
     *
     * @param newKey `true` quando a carteira já tinha vínculo local com outra chave (perda, R25).
     */
    data class BindingDevice(val newKey: Boolean, val cost: TxCost) : RegistrationProgress

    /** A carteira vai abrir para aprovar o registro, com o [cost] já estimado (FR-003). */
    data class AwaitingWallet(val cost: TxCost) : RegistrationProgress
}
