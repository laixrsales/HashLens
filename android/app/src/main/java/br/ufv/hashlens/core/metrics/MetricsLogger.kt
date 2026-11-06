package br.ufv.hashlens.core.metrics

import java.time.Duration

/** Medidas de tempo para o capítulo de resultados do TCC (T096). */
enum class Metric(val csvName: String) {
    /** SC-007: da imagem escolhida no Photo Picker até o resultado na tela. */
    VERIFICATION("verificacao"),

    /** SC-008: da foto tirada até a transação aprovada na carteira e enviada (inclui o tempo na carteira). */
    CAPTURE_TO_SUBMITTED("captura_ate_envio"),

    /** Como [CAPTURE_TO_SUBMITTED], a partir de "Salvar e registrar" no Editor. */
    EDIT_TO_SUBMITTED("edicao_ate_envio"),

    /**
     * SC-008, reportada à parte: do envio da transação até o app perceber a confirmação. A precisão é a
     * do acompanhamento: ~4 s com o Acompanhamento aberto, o ciclo do worker com o app fechado.
     */
    CONFIRMATION("confirmacao_rede")
}

/**
 * Registra medidas de tempo, sem pixels, hashes nem endereços: só qual medida, quanto durou e como
 * terminou ([outcome], ex.: "Submitted", "Error:NETWORK").
 */
fun interface MetricsLogger {
    fun record(metric: Metric, duration: Duration, outcome: String)
}
