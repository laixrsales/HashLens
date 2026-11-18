package br.ufv.hashlens.attestation

import java.time.Instant
import java.time.YearMonth

/** Resultado da [AttestationPolicy] (`contracts/attestation-policy.md` §4). */
sealed interface AttestationVerdict {
    data class Accepted(
        val securityLevel: SecurityLevel,
        val osPatchLevel: YearMonth,
        /** Sempre [BootState.VERIFIED] quando aceito (regra 4). */
        val bootState: BootState,
        val issuedAt: Instant,
        /** Seriais da cadeia em hex minúsculo, da folha para a raiz, para a consulta de revogação. */
        val certSerials: List<String>
    ) : AttestationVerdict

    data class Rejected(val reason: RejectReason) : AttestationVerdict
}

/** Motivos de recusa na ordem da policy §3; quando várias regras falham, vale a primeira. */
enum class RejectReason {
    CHAIN_INVALID,
    REVOKED,
    NOT_HARDWARE,
    BOOT_NOT_VERIFIED,
    PATCH_TOO_OLD,
    APP_NOT_GENUINE,
    KEY_PROPERTIES,
    KEY_MISMATCH,
    CHALLENGE_INVALID
}

/** Onde a chave do aparelho vive (regra 3). */
enum class SecurityLevel { TEE, STRONGBOX }

/** `rootOfTrust.verifiedBootState` da extensão de atestação. */
enum class BootState { VERIFIED, SELF_SIGNED, UNVERIFIED, FAILED }
