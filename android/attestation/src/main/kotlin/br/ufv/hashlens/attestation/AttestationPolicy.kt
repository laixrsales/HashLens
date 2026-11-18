package br.ufv.hashlens.attestation

import com.android.keyattestation.verifier.AttestationApplicationIdConstraint
import com.android.keyattestation.verifier.GoogleTrustAnchors
import com.android.keyattestation.verifier.IgnoredConstraint
import com.android.keyattestation.verifier.KeyDescription
import com.android.keyattestation.verifier.Origin
import com.android.keyattestation.verifier.SecurityLevel as LibSecurityLevel
import com.android.keyattestation.verifier.VerificationResult
import com.android.keyattestation.verifier.VerifiedBootState
import com.android.keyattestation.verifier.Verifier
import com.android.keyattestation.verifier.constraintConfig
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * O que a cadeia precisa provar (`contracts/attestation-policy.md` §1).
 *
 * @property allowedCertDigests SHA-256 dos certificados de assinatura do APK aceitos (hex, com ou
 *   sem `:`, qualquer caixa).
 * @property referenceTime instante em que a validade dos certificados e a janela do desafio são
 *   conferidas: agora, no atestador; `device.registeredAt`, na verificação.
 */
data class ExpectedAttestation(
    val chainId: Long,
    val contract: String,
    val wallet: String,
    val publicKey: ByteArray,
    val allowedCertDigests: Set<String>,
    val referenceTime: Instant,
    val window: ChallengeWindow,
    val packageName: String = HASHLENS_PACKAGE
) {
    companion object {
        const val HASHLENS_PACKAGE = "br.ufv.hashlens"
    }
}

/**
 * Política de atestação do HashLens (`contracts/attestation-policy.md` §3), a mesma no atestador e
 * na verificação do app (research R4).
 *
 * A biblioteca `android/keyattestation` valida a cadeia até as raízes (regra 1); as regras 2 a 9
 * são aplicadas aqui, na ordem da tabela, sobre a extensão da folha que a biblioteca já validou.
 * Quando várias falham, o motivo é o da primeira.
 *
 * @param trustAnchors raízes aceitas; nos testes, a raiz de teste.
 * @param revokedSerials seriais revogados ou suspensos, em hex minúsculo (regra 2). Na verificação
 *   do app fica vazio: a revogação é consultada à parte e não reprova (policy §5).
 */
class AttestationPolicy(
    private val trustAnchors: () -> Set<TrustAnchor> = GoogleTrustAnchors,
    private val revokedSerials: () -> Set<String> = { emptySet() }
) {
    fun evaluate(chain: List<ByteArray>, expected: ExpectedAttestation): AttestationVerdict {
        val certificates = parse(chain)?.takeIf { chainIsValid(it, expected.referenceTime) }
        val description = certificates?.let { runCatching { KeyDescription.parseFrom(it.first()) }.getOrNull() }
        if (certificates == null || description == null) return AttestationVerdict.Rejected(RejectReason.CHAIN_INVALID)
        return Evaluation(certificates, description, expected, revokedSerials()).verdict()
    }

    private fun parse(chain: List<ByteArray>): List<X509Certificate>? = runCatching {
        val factory = CertificateFactory.getInstance("X.509")
        chain.map { factory.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * Regra 1, pela biblioteca: assinaturas e nomes até uma das [trustAnchors], validade em
     * [referenceTime], extensão só na folha. As outras restrições da biblioteca ficam desligadas
     * porque a policy as confere na própria ordem; a revogação também (regra 2, em [Evaluation]).
     */
    private fun chainIsValid(certificates: List<X509Certificate>, referenceTime: Instant): Boolean {
        val verifier = Verifier(
            trustAnchorsSource = trustAnchors,
            revokedSerialsSource = { emptySet() },
            instantSource = { referenceTime },
            constraintConfig = constraintConfig {
                keyOrigin { IgnoredConstraint }
                securityLevel { IgnoredConstraint }
                rootOfTrust { IgnoredConstraint }
                attestationApplicationId { AttestationApplicationIdConstraint.NONE }
            }
        )
        return runCatching { verifier.verify(certificates) }.getOrNull() is VerificationResult.Success
    }

    /** Regras 2 a 9 sobre uma cadeia já validada até a raiz. */
    private class Evaluation(
        certificates: List<X509Certificate>,
        private val description: KeyDescription,
        private val expected: ExpectedAttestation,
        private val revoked: Set<String>
    ) {
        private val leaf = certificates.first()
        private val serials = certificates.map { it.serialNumber.toString(SERIAL_RADIX) }
        private val hardware = description.hardwareEnforced
        private val challenge = AttestationChallenge.decode(description.attestationChallenge.toByteArray())
        private val securityLevel = securityLevel()
        private val patch = hardware.osPatchLevel?.yearMonth

        /** Na ordem da policy §3. */
        private val rules: List<Pair<RejectReason, () -> Boolean>> = listOf(
            RejectReason.REVOKED to { serials.none { it in revoked } },
            RejectReason.NOT_HARDWARE to { securityLevel != null },
            RejectReason.BOOT_NOT_VERIFIED to {
                hardware.rootOfTrust?.let { it.verifiedBootState == VerifiedBootState.VERIFIED && it.deviceLocked } ==
                    true
            },
            RejectReason.PATCH_TOO_OLD to ::patchIsRecent,
            RejectReason.APP_NOT_GENUINE to ::isGenuineApp,
            RejectReason.KEY_PROPERTIES to ::hasExpectedKeyProperties,
            RejectReason.KEY_MISMATCH to ::leafKeyMatches,
            RejectReason.CHALLENGE_INVALID to {
                challenge != null &&
                    challenge.matches(expected.chainId, expected.contract, expected.wallet) &&
                    challenge.isWithin(expected.window, expected.referenceTime)
            }
        )

        fun verdict(): AttestationVerdict {
            val failed = rules.firstOrNull { (_, satisfied) -> !satisfied() }?.first
            if (failed != null) return AttestationVerdict.Rejected(failed)
            return AttestationVerdict.Accepted(
                securityLevel = checkNotNull(securityLevel),
                osPatchLevel = checkNotNull(patch),
                bootState = BootState.VERIFIED,
                issuedAt = checkNotNull(challenge).issuedAt,
                certSerials = serials
            )
        }

        /** Regra 3: TEE ou StrongBox, e o KeyMint no mesmo nível da atestação. */
        private fun securityLevel(): SecurityLevel? = when {
            description.keyMintSecurityLevel != description.attestationSecurityLevel -> null
            description.attestationSecurityLevel == LibSecurityLevel.TRUSTED_ENVIRONMENT -> SecurityLevel.TEE
            description.attestationSecurityLevel == LibSecurityLevel.STRONG_BOX -> SecurityLevel.STRONGBOX
            else -> null
        }

        /** Regra 5: no máximo 24 meses antes de `issuedAt` (sem desafio legível, a regra 9 recusa depois). */
        private fun patchIsRecent(): Boolean {
            val reference = YearMonth.from((challenge?.issuedAt ?: expected.referenceTime).atZone(ZoneOffset.UTC))
            return patch != null && ChronoUnit.MONTHS.between(patch, reference) <= MAX_PATCH_AGE_MONTHS
        }

        /** Regra 6: o pacote do HashLens, assinado por um dos certificados aceitos. */
        private fun isGenuineApp(): Boolean {
            val applicationId = description.softwareEnforced.attestationApplicationId
                ?: hardware.attestationApplicationId
                ?: return false
            val allowed = expected.allowedCertDigests.map(::normalizeDigest).toSet()
            return applicationId.packages.any { it.name == expected.packageName } &&
                applicationId.signatures.any { hex(it.toByteArray()) in allowed }
        }

        /**
         * Regra 7, na parte garantida pelo hardware: só assinatura, EC P-256, SHA-256 entre os
         * digests e chave gerada no próprio Keystore. Uma chave importada (`IMPORTED`,
         * `SECURELY_IMPORTED`) tem cópia da chave privada fora do aparelho, com a qual se assinaria
         * qualquer imagem.
         */
        private fun hasExpectedKeyProperties(): Boolean = hardware.purposes == setOf(PURPOSE_SIGN) &&
            hardware.algorithms == ALGORITHM_EC &&
            hardware.ecCurve == CURVE_P256 &&
            hardware.digests?.contains(DIGEST_SHA256) == true &&
            hardware.origin == Origin.GENERATED

        /** Regra 8: chave da folha = `publicKey` esperada (65 bytes, `0x04 ‖ X ‖ Y`). */
        private fun leafKeyMatches(): Boolean {
            val key = leaf.publicKey as? ECPublicKey ?: return false
            val encoded = byteArrayOf(UNCOMPRESSED_PREFIX) + fixed(key.w.affineX) + fixed(key.w.affineY)
            return encoded.contentEquals(expected.publicKey)
        }
    }

    private companion object {
        const val MAX_PATCH_AGE_MONTHS = 24L
        const val SERIAL_RADIX = 16
        const val COORDINATE_SIZE = 32
        const val UNCOMPRESSED_PREFIX: Byte = 0x04

        // KeyMint: KeyPurpose.SIGN, Algorithm.EC, EcCurve.P_256, Digest.SHA_2_256
        val PURPOSE_SIGN: BigInteger = BigInteger.valueOf(2)
        val ALGORITHM_EC: BigInteger = BigInteger.valueOf(3)
        val CURVE_P256: BigInteger = BigInteger.ONE
        val DIGEST_SHA256: BigInteger = BigInteger.valueOf(4)

        fun fixed(value: BigInteger): ByteArray {
            val magnitude = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            return if (magnitude.size >= COORDINATE_SIZE) {
                magnitude
            } else {
                ByteArray(COORDINATE_SIZE - magnitude.size) + magnitude
            }
        }

        fun normalizeDigest(digest: String): String = digest.trim().lowercase().removePrefix("0x").replace(":", "")

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
