package br.ufv.hashlens.domain.model

import java.time.Instant
import java.time.YearMonth

/**
 * Origem atestada de um registro (020 data-model §3): o que a revalidação da cadeia de atestação do
 * aparelho, lida da rede, comprova (`contracts/attestation-policy.md` §5, FR-014).
 *
 * @property boundAt horário do vínculo do aparelho (`Device.registeredAt`).
 */
data class DeviceOrigin(
    val securityLevel: SecurityLevel,
    val bootState: BootState,
    val osPatchLevel: YearMonth,
    val boundAt: Instant,
    val revocation: RevocationStatus
)

/** Onde vive a chave do aparelho: ambiente seguro do processador (TEE) ou chip dedicado (StrongBox). */
enum class SecurityLevel { TEE, STRONGBOX }

/** Estado do sistema na atestação; a política só aceita [VERIFIED]. */
enum class BootState { VERIFIED }

/**
 * Situação do certificado do aparelho na lista de revogação do Google (FR-017). O atestador recusa
 * cadeias já revogadas no vínculo, então [REVOKED_AFTER_BINDING] é sempre posterior a ele.
 */
enum class RevocationStatus { NOT_REVOKED, REVOKED_AFTER_BINDING, UNKNOWN }
