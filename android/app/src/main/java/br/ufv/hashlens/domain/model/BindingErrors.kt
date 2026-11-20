package br.ufv.hashlens.domain.model

/**
 * O atestador recusou o aparelho (`attester-api.md` §4, FR-010). Acontece antes de abrir a carteira:
 * nenhuma taxa é cobrada, e a imagem fica `LOCAL_ONLY`.
 */
class DeviceNotEligibleException(val reason: NotEligibleReason) : Exception("aparelho não elegível: $reason")

/** Motivo da recusa, como a pessoa vê (020 data-model §3). */
enum class NotEligibleReason {
    /** Sistema modificado: boot não verificado ou bootloader destravado. */
    MODIFIED,

    /** Patch de segurança com mais de 24 meses. */
    OUTDATED,

    /** Sem TEE ou StrongBox, cadeia inválida ou chave sem as propriedades exigidas. */
    NO_SECURE_HARDWARE,

    /** O aplicativo não é o HashLens original. */
    APP_NOT_GENUINE,

    /** Certificado da cadeia revogado ou suspenso pelo Google. */
    REVOKED
}

/**
 * O atestador não respondeu, respondeu com erro ou não está configurado (FR-011). Falha temporária:
 * a pessoa pode tentar de novo; a carteira não é aberta.
 */
class AttesterUnavailableException(cause: Throwable? = null) : Exception("atestador indisponível", cause)

/**
 * O atestador recusou o desafio ou a chave (`CHALLENGE_INVALID`, `KEY_MISMATCH`): em geral um
 * desafio vencido ou o relógio do aparelho errado. O vínculo gera a chave de novo e tenta uma vez
 * mais; se repetir, vira [AttesterUnavailableException] (`attester-api.md` §4).
 */
class AttestationRetryException(val reason: String) : Exception("nova tentativa de atestação: $reason")
