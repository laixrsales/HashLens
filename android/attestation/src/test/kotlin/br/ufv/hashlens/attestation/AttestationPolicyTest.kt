package br.ufv.hashlens.attestation

import br.ufv.hashlens.attestation.fixtures.TestChains
import br.ufv.hashlens.attestation.fixtures.TestChains.Params
import com.android.keyattestation.verifier.GoogleTrustAnchors
import com.android.keyattestation.verifier.Origin
import com.android.keyattestation.verifier.SecurityLevel as LibSecurityLevel
import com.android.keyattestation.verifier.VerifiedBootState
import com.google.gson.JsonParser
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.util.Base64
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 020/T020: política de `contracts/attestation-policy.md` §2 e §3 com a cadeia real de T016 e as
 * cadeias sintéticas de T017.
 */
class AttestationPolicyTest {
    private val chainId = 11_155_111L
    private val contract = "0xfBa7aeDa5e979F88336b35e81A1780b4655BAe6E"
    private val wallet = "0x1234567890AbcdEF1234567890aBcdef12345678"
    private val otherAddress = "0x00000000000000000000000000000000000000aa"
    private val now = Instant.parse("2026-11-18T12:00:00Z")

    private fun challenge(
        issuedAt: Instant = now.minusSeconds(60),
        chainId: Long = this.chainId,
        contract: String = this.contract,
        wallet: String = this.wallet
    ) = AttestationChallenge(chainId, contract, wallet, issuedAt).encode()

    private fun expected(
        publicKey: ByteArray,
        referenceTime: Instant = now,
        window: ChallengeWindow = ChallengeWindow.ATTESTER,
        allowed: Set<String> = setOf(TestChains.GENUINE_DIGEST)
    ) = ExpectedAttestation(chainId, contract, wallet, publicKey, allowed, referenceTime, window)

    private val syntheticPolicy = AttestationPolicy(trustAnchors = { TestChains.anchors })

    private fun evaluate(
        params: Params = Params(challenge = challenge()),
        policy: AttestationPolicy = syntheticPolicy,
        expectedFor: (TestChains.Generated) -> ExpectedAttestation = { expected(it.publicKey) }
    ): AttestationVerdict {
        val generated = TestChains.generate(params)
        return policy.evaluate(generated.chain, expectedFor(generated))
    }

    private fun rejected(reason: RejectReason) = AttestationVerdict.Rejected(reason)

    // ───────────────────────────── Cadeia real ──────────────────────

    @Nested
    inner class CadeiaReal {
        private val json = JsonParser.parseString(
            javaClass.getResource("/attestation/real/SM_M526B-tee.json")!!.readText()
        ).asJsonObject
        private val chain = json.getAsJsonArray("chain").map { Base64.getDecoder().decode(it.asString) }
        private val publicKey = hexBytes(json.get("publicKey").asString)
        private val capturedAt = Instant.ofEpochSecond(json.get("capturedAt").asLong)

        /** Digest do certificado de debug do app, o que assinou o APK da captura. */
        private val debugDigest = "afaa9a6a0d6ee2af62a12be6de02686175186baed39b5c1635a412288579eda2"
        private val googlePolicy = AttestationPolicy()

        private fun expectedReal(
            referenceTime: Instant = capturedAt,
            window: ChallengeWindow = ChallengeWindow.ATTESTER,
            allowed: Set<String> = setOf(debugDigest),
            wallet: String = json.get("wallet").asString
        ) = ExpectedAttestation(
            json.get("chainId").asLong,
            json.get("contract").asString,
            wallet,
            publicKey,
            allowed,
            referenceTime,
            window
        )

        @Test
        fun `aceita a cadeia do aparelho com as raizes do Google`() {
            val verdict = googlePolicy.evaluate(chain, expectedReal())

            val accepted = verdict.shouldBeInstanceOf<AttestationVerdict.Accepted>()
            accepted.securityLevel shouldBe SecurityLevel.TEE
            accepted.osPatchLevel shouldBe YearMonth.of(2025, 5)
            accepted.bootState shouldBe BootState.VERIFIED
            accepted.issuedAt shouldBe capturedAt
            accepted.certSerials.size shouldBe 4
            accepted.certSerials.last() shouldBe "d50ff25ba3f2d6b3"
            accepted.certSerials.forEach { it shouldBe it.lowercase() }
        }

        @Test
        fun `na verificacao, com referenceTime = registeredAt ate 30 min depois, continua aceita`() {
            val verdict = googlePolicy.evaluate(
                chain,
                expectedReal(
                    referenceTime = capturedAt.plus(Duration.ofMinutes(30)),
                    window = ChallengeWindow.VERIFICATION
                )
            )
            verdict.shouldBeInstanceOf<AttestationVerdict.Accepted>()
        }

        @Test
        fun `sem o certificado do APK na lista, aplicativo nao original`() {
            googlePolicy.evaluate(chain, expectedReal(allowed = setOf(TestChains.OTHER_DIGEST))) shouldBe
                rejected(RejectReason.APP_NOT_GENUINE)
            googlePolicy.evaluate(chain, expectedReal(allowed = emptySet())) shouldBe
                rejected(RejectReason.APP_NOT_GENUINE)
        }

        @Test
        fun `para outra carteira ou fora da janela, desafio invalido`() {
            googlePolicy.evaluate(chain, expectedReal(wallet = otherAddress)) shouldBe
                rejected(RejectReason.CHALLENGE_INVALID)
            googlePolicy.evaluate(chain, expectedReal(referenceTime = capturedAt.plus(Duration.ofMinutes(16)))) shouldBe
                rejected(RejectReason.CHALLENGE_INVALID)
        }

        @Test
        fun `com outra chave esperada, chave divergente`() {
            val other = TestChains.generate(Params(challenge = challenge())).publicKey
            googlePolicy.evaluate(chain, expectedReal().copy(publicKey = other)) shouldBe
                rejected(RejectReason.KEY_MISMATCH)
        }

        @Test
        fun `com a raiz de teste no lugar das do Google, cadeia invalida`() {
            syntheticPolicy.evaluate(chain, expectedReal()) shouldBe rejected(RejectReason.CHAIN_INVALID)
        }

        @Test
        fun `serial da cadeia na lista de revogacao, revogado`() {
            val revoked = AttestationPolicy(revokedSerials = { setOf("c14774456da1df07d6d8c3ca8dc5af9a") })
            revoked.evaluate(chain, expectedReal()) shouldBe rejected(RejectReason.REVOKED)
        }
    }

    // ───────────────────────────── Cadeia sintética aceita ──────────

    @Test
    fun `cadeia sintetica padrao e aceita, com os dados do veredito`() {
        val issuedAt = now.minusSeconds(60)
        val generated = TestChains.generate(Params(challenge = challenge(issuedAt)))

        val verdict = syntheticPolicy.evaluate(generated.chain, expected(generated.publicKey))

        verdict shouldBe AttestationVerdict.Accepted(
            securityLevel = SecurityLevel.TEE,
            osPatchLevel = YearMonth.of(2026, 9),
            bootState = BootState.VERIFIED,
            issuedAt = issuedAt,
            certSerials = generated.serials
        )
    }

    @Test
    fun `StrongBox e aceito`() {
        val verdict = evaluate(Params(challenge = challenge(), attestationSecurityLevel = LibSecurityLevel.STRONG_BOX))
        verdict.shouldBeInstanceOf<AttestationVerdict.Accepted>().securityLevel shouldBe SecurityLevel.STRONGBOX
    }

    @Test
    fun `digest da lista em maiusculas ou com dois-pontos tambem vale`() {
        val upper = TestChains.GENUINE_DIGEST.uppercase().chunked(2).joinToString(":")
        evaluate { expected(it.publicKey, allowed = setOf(upper)) }.shouldBeInstanceOf<AttestationVerdict.Accepted>()
    }

    // ───────────────────────────── Um caso por motivo ───────────────

    @Test
    fun `CHAIN_INVALID`() {
        evaluate(Params(challenge = challenge(), leafSignedByStranger = true)) shouldBe
            rejected(RejectReason.CHAIN_INVALID)
        // Só a folha pode ter a extensão de atestação
        evaluate(Params(challenge = challenge(), extensionInIntermediate = true)) shouldBe
            rejected(RejectReason.CHAIN_INVALID)
        // Raiz de teste não é raiz do Google
        evaluate(policy = AttestationPolicy(trustAnchors = GoogleTrustAnchors)) shouldBe
            rejected(RejectReason.CHAIN_INVALID)

        val generated = TestChains.generate(Params(challenge = challenge()))
        syntheticPolicy.evaluate(listOf(byteArrayOf(1, 2, 3)), expected(generated.publicKey)) shouldBe
            rejected(RejectReason.CHAIN_INVALID)
        syntheticPolicy.evaluate(generated.chain.take(2), expected(generated.publicKey)) shouldBe
            rejected(RejectReason.CHAIN_INVALID)
        syntheticPolicy.evaluate(emptyList(), expected(generated.publicKey)) shouldBe
            rejected(RejectReason.CHAIN_INVALID)
    }

    @Test
    fun `REVOKED`() {
        val generated = TestChains.generate(Params(challenge = challenge()))
        for (serial in generated.serials) {
            AttestationPolicy(trustAnchors = { TestChains.anchors }, revokedSerials = { setOf(serial) })
                .evaluate(generated.chain, expected(generated.publicKey)) shouldBe rejected(RejectReason.REVOKED)
        }
    }

    @Test
    fun `REVOKED tambem com o serial em decimal, como parte da lista do Google`() {
        val generated = TestChains.generate(Params(challenge = challenge()))
        val decimal = generated.certificates[1].serialNumber.toString()

        AttestationPolicy(trustAnchors = { TestChains.anchors }, revokedSerials = { setOf(decimal) })
            .evaluate(generated.chain, expected(generated.publicKey)) shouldBe rejected(RejectReason.REVOKED)
    }

    @Test
    fun `NOT_HARDWARE`() {
        evaluate(Params(challenge = challenge(), attestationSecurityLevel = LibSecurityLevel.SOFTWARE)) shouldBe
            rejected(RejectReason.NOT_HARDWARE)
        evaluate(
            Params(
                challenge = challenge(),
                attestationSecurityLevel = LibSecurityLevel.STRONG_BOX,
                keyMintSecurityLevel = LibSecurityLevel.TRUSTED_ENVIRONMENT
            )
        ) shouldBe rejected(RejectReason.NOT_HARDWARE)
    }

    @Test
    fun `BOOT_NOT_VERIFIED`() {
        evaluate(Params(challenge = challenge(), bootState = VerifiedBootState.UNVERIFIED)) shouldBe
            rejected(RejectReason.BOOT_NOT_VERIFIED)
        evaluate(Params(challenge = challenge(), bootState = VerifiedBootState.SELF_SIGNED)) shouldBe
            rejected(RejectReason.BOOT_NOT_VERIFIED)
        evaluate(Params(challenge = challenge(), deviceLocked = false)) shouldBe
            rejected(RejectReason.BOOT_NOT_VERIFIED)
    }

    @Test
    fun `PATCH_TOO_OLD, com 24 meses aceito e 24 mais 1 recusado`() {
        // issuedAt em 2026-11: 2024-11 tem exatamente 24 meses
        evaluate(Params(challenge = challenge(), osPatchLevel = YearMonth.of(2024, 11)))
            .shouldBeInstanceOf<AttestationVerdict.Accepted>()
        evaluate(Params(challenge = challenge(), osPatchLevel = YearMonth.of(2024, 10))) shouldBe
            rejected(RejectReason.PATCH_TOO_OLD)
        evaluate(Params(challenge = challenge(), osPatchLevel = null)) shouldBe rejected(RejectReason.PATCH_TOO_OLD)
    }

    @Test
    fun `o limite do patch conta a partir de issuedAt, tambem na verificacao`() {
        // Vínculo feito em 2026-11 com patch 2024-11; verificado em 2027: continua aceito
        val issuedAt = now.minusSeconds(60)
        val generated = TestChains.generate(
            Params(challenge = challenge(issuedAt), osPatchLevel = YearMonth.of(2024, 11))
        )
        syntheticPolicy.evaluate(
            generated.chain,
            expected(generated.publicKey, referenceTime = now, window = ChallengeWindow.VERIFICATION)
        ).shouldBeInstanceOf<AttestationVerdict.Accepted>()
    }

    @Test
    fun `APP_NOT_GENUINE`() {
        evaluate(Params(challenge = challenge(), packageName = "br.ufv.hashlens.clone")) shouldBe
            rejected(RejectReason.APP_NOT_GENUINE)
        evaluate(Params(challenge = challenge(), signatureDigest = TestChains.OTHER_DIGEST)) shouldBe
            rejected(RejectReason.APP_NOT_GENUINE)
        evaluate { expected(it.publicKey, allowed = emptySet()) } shouldBe rejected(RejectReason.APP_NOT_GENUINE)
    }

    @Test
    fun `KEY_PROPERTIES`() {
        val base = Params(challenge = challenge())
        evaluate(base.copy(purposes = setOf(TestChains.PURPOSE_SIGN, TestChains.PURPOSE_DECRYPT))) shouldBe
            rejected(RejectReason.KEY_PROPERTIES)
        evaluate(base.copy(algorithm = TestChains.ALGORITHM_RSA)) shouldBe rejected(RejectReason.KEY_PROPERTIES)
        evaluate(base.copy(ecCurve = TestChains.CURVE_P384)) shouldBe rejected(RejectReason.KEY_PROPERTIES)
        evaluate(base.copy(digests = setOf(TestChains.DIGEST_NONE))) shouldBe rejected(RejectReason.KEY_PROPERTIES)
        // Chave importada: quem importou conhece a chave privada e assinaria qualquer coisa
        evaluate(base.copy(origin = Origin.IMPORTED)) shouldBe rejected(RejectReason.KEY_PROPERTIES)
        evaluate(base.copy(origin = Origin.SECURELY_IMPORTED)) shouldBe rejected(RejectReason.KEY_PROPERTIES)
        // SHA-256 entre outros digests é aceito ("digest ∋ SHA_256")
        evaluate(base.copy(digests = setOf(TestChains.DIGEST_NONE, TestChains.DIGEST_SHA256)))
            .shouldBeInstanceOf<AttestationVerdict.Accepted>()
    }

    @Test
    fun `KEY_MISMATCH`() {
        val other = TestChains.generate(Params(challenge = challenge())).publicKey
        evaluate { expected(other) } shouldBe rejected(RejectReason.KEY_MISMATCH)
    }

    @Test
    fun `CHALLENGE_INVALID`() {
        evaluate(Params(challenge = challenge(wallet = otherAddress))) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
        evaluate(Params(challenge = challenge(contract = otherAddress))) shouldBe
            rejected(RejectReason.CHALLENGE_INVALID)
        evaluate(Params(challenge = challenge(chainId = 1))) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
        evaluate(Params(challenge = "hashlens-t004".toByteArray())) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
        evaluate(Params(challenge = ByteArray(0))) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
    }

    // ───────────────────────────── Janelas do desafio ───────────────

    @Test
    fun `janela do atestador, menos 15 min e mais 2 min em relacao a agora`() {
        fun at(offset: Duration) = evaluate(Params(challenge = challenge(issuedAt = now.plus(offset))))

        at(Duration.ofMinutes(-15)).shouldBeInstanceOf<AttestationVerdict.Accepted>()
        at(Duration.ofMinutes(-15).minusSeconds(1)) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
        at(Duration.ofMinutes(2)).shouldBeInstanceOf<AttestationVerdict.Accepted>()
        at(Duration.ofMinutes(2).plusSeconds(1)) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
    }

    @Test
    fun `janela da verificacao, menos 30 min e mais 2 min em relacao a registeredAt`() {
        val registeredAt = now
        fun at(offset: Duration) = evaluate(Params(challenge = challenge(issuedAt = registeredAt.plus(offset)))) {
            expected(it.publicKey, referenceTime = registeredAt, window = ChallengeWindow.VERIFICATION)
        }

        at(Duration.ofMinutes(-29)).shouldBeInstanceOf<AttestationVerdict.Accepted>()
        at(Duration.ofMinutes(-30)).shouldBeInstanceOf<AttestationVerdict.Accepted>()
        at(Duration.ofMinutes(-30).minusSeconds(1)) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
        at(Duration.ofMinutes(-31)) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
        at(Duration.ofMinutes(2)).shouldBeInstanceOf<AttestationVerdict.Accepted>()
        at(Duration.ofMinutes(2).plusSeconds(1)) shouldBe rejected(RejectReason.CHALLENGE_INVALID)
    }

    // ───────────────────────────── Ordem dos motivos ────────────────

    @Test
    fun `quando varias regras falham, vale a primeira da tabela`() {
        // copy() não recalcula o padrão de keyMintSecurityLevel: os dois níveis mudam juntos
        val tee = LibSecurityLevel.TRUSTED_ENVIRONMENT
        val everythingWrong = Params(
            challenge = challenge(wallet = otherAddress),
            attestationSecurityLevel = LibSecurityLevel.SOFTWARE,
            bootState = VerifiedBootState.UNVERIFIED,
            osPatchLevel = YearMonth.of(2010, 1),
            packageName = "outro.app",
            origin = Origin.IMPORTED
        )
        evaluate(everythingWrong.copy(leafSignedByStranger = true)) shouldBe rejected(RejectReason.CHAIN_INVALID)
        evaluate(everythingWrong) shouldBe rejected(RejectReason.NOT_HARDWARE)
        evaluate(everythingWrong.copy(attestationSecurityLevel = tee, keyMintSecurityLevel = tee)) shouldBe
            rejected(RejectReason.BOOT_NOT_VERIFIED)
        evaluate(
            everythingWrong.copy(
                attestationSecurityLevel = tee,
                keyMintSecurityLevel = tee,
                bootState = VerifiedBootState.VERIFIED
            )
        ) shouldBe rejected(RejectReason.PATCH_TOO_OLD)
        evaluate(
            everythingWrong.copy(
                attestationSecurityLevel = tee,
                keyMintSecurityLevel = tee,
                bootState = VerifiedBootState.VERIFIED,
                osPatchLevel = YearMonth.of(2026, 9)
            )
        ) shouldBe rejected(RejectReason.APP_NOT_GENUINE)
        evaluate(
            everythingWrong.copy(
                attestationSecurityLevel = tee,
                keyMintSecurityLevel = tee,
                bootState = VerifiedBootState.VERIFIED,
                osPatchLevel = YearMonth.of(2026, 9),
                packageName = TestChains.PACKAGE
            )
        ) shouldBe rejected(RejectReason.KEY_PROPERTIES)

        // Revogada e com o resto errado: REVOKED vem antes de NOT_HARDWARE
        val generated = TestChains.generate(everythingWrong)
        AttestationPolicy(trustAnchors = { TestChains.anchors }, revokedSerials = { setOf(generated.serials[1]) })
            .evaluate(generated.chain, expected(generated.publicKey)) shouldBe rejected(RejectReason.REVOKED)

        // Chave divergente e desafio errado: KEY_MISMATCH vem antes
        val other = TestChains.generate(Params(challenge = challenge())).publicKey
        evaluate(Params(challenge = challenge(wallet = otherAddress))) { expected(other) } shouldBe
            rejected(RejectReason.KEY_MISMATCH)
    }

    private fun hexBytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
