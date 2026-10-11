package br.ufv.hashlens.domain.model

/**
 * Resultado da verificação de uma imagem (verification-result §2; spec FR-021). Os textos de cada
 * caso estão em verification-result §3.
 */
sealed interface VerificationResult {
    /** O arquivo é idêntico (SHA-256) a uma captura registrada com assinatura válida. */
    data class RegisteredOriginal(val record: RecordView) : VerificationResult

    /**
     * O arquivo é idêntico a uma versão editada registrada.
     *
     * @property original raiz da cadeia de edições ([ImageRecord.originalId]).
     * @property percentFromOriginal alteração visual em relação à original, 0..100 com 1 casa (R17).
     */
    data class RegisteredEdited(val record: RecordView, val original: RecordView, val percentFromOriginal: Double) :
        VerificationResult

    /**
     * Nenhum SHA-256 igual, mas o pHash é igual ao de registros com assinatura válida (ex.: cópia
     * recomprimida).
     *
     * @property candidates em ordem cronológica de registro.
     */
    data class VisualMatch(val candidates: List<Candidate>) : VerificationResult {
        data class Candidate(val record: RecordView, val original: RecordView, val percentFromOriginal: Double)
    }

    /** A assinatura do aparelho não confere com os dados registrados de [record]. */
    data class Tampered(val record: RecordView) : VerificationResult

    /** Nenhum registro encontrado; não significa que a imagem seja falsa. */
    data object NotRegistered : VerificationResult

    /** A verificação não chegou a um resultado; nunca é apresentada como "Não registrada". */
    data class Error(val reason: Reason) : VerificationResult {
        enum class Reason {
            /** O arquivo não decodifica como imagem (ex.: HEIC; FR-040). */
            UNSUPPORTED_FILE,

            /** Sem conexão, tempo esgotado ou falha de HTTP ao consultar o contrato. */
            NETWORK,

            /** O nó respondeu com erro ou com dados inconsistentes. */
            UNKNOWN
        }
    }
}
