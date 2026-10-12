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

/** Registro on-chain pronto para exibição: hashes em hex e operações decodificadas. */
fun ImageRecord.toView(): RecordView = RecordView(
    id = id,
    sha256 = sha256.hex,
    pHash = pHash.hex,
    registrant = registrant,
    deviceId = deviceId,
    timestamp = timestamp,
    operations = decodedOperations(),
    // getRecord não informa a transação que criou o registro
    txUrl = null
)

// O contrato só limita o tamanho; um texto fora do formato do app (hashing-spec §4) não invalida o registro
private fun ImageRecord.decodedOperations(): List<EditOperation> = try {
    EditOperationCodec.decode(operations)
} catch (_: IllegalArgumentException) {
    emptyList()
}
