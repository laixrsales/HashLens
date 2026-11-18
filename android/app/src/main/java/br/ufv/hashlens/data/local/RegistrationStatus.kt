package br.ufv.hashlens.data.local

/**
 * Máquina de estados do registro de uma imagem ou de um vínculo de aparelho (data-model §2.3).
 *
 * `CONFIRMED` é terminal. Nenhum registro sai de [PENDING] para [FAILED] sem antes reconciliar por
 * `getIdBySha256`.
 */
enum class RegistrationStatus {
    /** Gravado na galeria e com hashes calculados; ainda sem pedido à carteira. */
    LOCAL_ONLY,

    /** Transação aberta na carteira, aguardando a aprovação da pessoa. */
    AWAITING_WALLET,

    /** Transação enviada (`txHash` e `submittedAt` definidos), sem recibo ainda. */
    PENDING,

    /** Registro on-chain confirmado (`recordId` ou `deviceId` definido). */
    CONFIRMED,

    /** Não registrado; o motivo fica em [FailureReason]. */
    FAILED
}

/** Motivo de [RegistrationStatus.FAILED] (data-model §2.1). */
enum class FailureReason {
    /** A pessoa recusou a transação na carteira. */
    REJECTED,

    /** Recibo com status 0 e o hash não está on-chain. */
    REVERTED,

    /** Sem recibo dentro do prazo de `ChainConfig.pendingTimeout` e o hash não está on-chain (R23). */
    TIMEOUT,

    /** O arquivo relido da galeria não tem o SHA-256 calculado antes da gravação (R8). */
    HASH_MISMATCH,

    /** Falha de rede ao consultar o contrato ou ao falar com a carteira. */
    NETWORK,

    /** O saldo da carteira não cobre o gás estimado; a carteira nem foi aberta (FR-003). */
    INSUFFICIENT_FUNDS
}

/**
 * Motivo de [RegistrationStatus.FAILED] de um vínculo de aparelho (020 data-model §2.1), para a UI e
 * para o reenvio (FR-010, FR-011). Os `NOT_ELIGIBLE_*` e [ATTESTER_UNAVAILABLE] acontecem antes de
 * abrir a carteira.
 */
enum class BindingFailure {
    /** Sistema modificado: boot não verificado ou bootloader destravado. */
    NOT_ELIGIBLE_MODIFIED,

    /** Patch de segurança com mais de 24 meses. */
    NOT_ELIGIBLE_OUTDATED,

    /** Sem TEE ou StrongBox, ou chave sem as propriedades exigidas. */
    NOT_ELIGIBLE_NO_SECURE_HARDWARE,

    /** O aplicativo não é o HashLens original (pacote ou certificado de assinatura). */
    NOT_ELIGIBLE_APP,

    /** Certificado da cadeia revogado ou suspenso pelo Google. */
    NOT_ELIGIBLE_REVOKED,

    /** Atestador fora do ar, sem rede ou recusa temporária (tentar de novo). */
    ATTESTER_UNAVAILABLE,

    /** O saldo da carteira não cobre o gás estimado. */
    INSUFFICIENT_FUNDS,

    /** Recibo com status 0. */
    REVERTED,

    /** Sem recibo dentro do prazo. */
    TIMEOUT
}

/** Como a imagem local surgiu. */
enum class ImageOrigin {
    CAPTURE,
    EDIT
}
