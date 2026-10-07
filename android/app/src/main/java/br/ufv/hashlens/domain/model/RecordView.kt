package br.ufv.hashlens.domain.model

import java.time.Instant

/**
 * Registro pronto para exibição (verification-result §2): hashes em hex e operações decodificadas.
 *
 * @property txUrl link do explorador para a transação do registro, quando conhecida.
 */
data class RecordView(
    val id: Long,
    val sha256: String,
    val pHash: String,
    val registrant: String,
    val deviceId: Int,
    val timestamp: Instant,
    val operations: List<EditOperation>,
    val txUrl: String?
)
