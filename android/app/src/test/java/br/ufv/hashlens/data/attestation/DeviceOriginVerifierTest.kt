package br.ufv.hashlens.data.attestation

import br.ufv.hashlens.attestation.AttestationPolicy
import br.ufv.hashlens.attestation.AttestationVerdict
import br.ufv.hashlens.attestation.ChallengeWindow
import br.ufv.hashlens.attestation.ExpectedAttestation
import br.ufv.hashlens.attestation.RejectReason
import br.ufv.hashlens.attestation.fixtures.TestChains
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.domain.model.RevocationStatus
import br.ufv.hashlens.testing.TestAttestation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * 020/T049: revalidação da atestação na verificação (`contracts/attestation-policy.md` §5,
 * `contracts/verification-result.md` §1) com cadeias sintéticas e um contrato falso.
 */
class DeviceOriginVerifierTest {
    private val config = TestAttestation.config(CONTRACT)
    private val reader = mockk<RegistryReader>()
    private val revocation = mockk<RevocationRepository>()
    private val verifier = TestAttestation.verifier(reader, config, revocation)
    private val key: KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    init {
        coEvery { revocation.status(any()) } returns RevocationStatus.NOT_REVOKED
    }

    /** Publica a cadeia no bloco do vínculo do contrato falso. */
    private fun published(bound: TestAttestation.Bound): TestAttestation.Bound = bound.also {
        coEvery { reader.getAttestationChain(it.device.id, it.device.registeredBlock) } returns it.chain
    }

    private fun bind(
        issuedAt: Instant = REGISTERED_AT.minusSeconds(60),
        challengeWallet: String = OWNER,
        params: (TestChains.Params) -> TestChains.Params = { it }
    ) = published(
        TestAttestation.bind(
            config,
            id = DEVICE_ID,
            owner = OWNER,
            key = key,
            registeredAt = REGISTERED_AT,
            issuedAt = issuedAt,
            challengeWallet = challengeWallet,
            params = params
        )
    )

    @Test
    fun `atestação válida devolve a origem com nível, sistema, patch e data do vínculo`() = runTest {
        val bound = bind()

        verifier.verify(bound.device) shouldBe TestAttestation.origin(bound.device)
    }

    @Test
    fun `hash da cadeia diferente do gravado no aparelho é adulterado`() = runTest {
        val bound = bind()
        val other = TestAttestation.bind(config, id = DEVICE_ID, owner = OWNER, key = key, registeredAt = REGISTERED_AT)
        coEvery { reader.getAttestationChain(DEVICE_ID, bound.device.registeredBlock) } returns other.chain

        verifier.verify(bound.device).shouldBeNull()
    }

    @Test
    fun `sem o evento DeviceAttested no bloco do vínculo é adulterado`() = runTest {
        val bound = bind()
        coEvery { reader.getAttestationChain(DEVICE_ID, bound.device.registeredBlock) } returns null

        verifier.verify(bound.device).shouldBeNull()
    }

    @Test
    fun `política recusa cadeia de outro pacote`() = runTest {
        val bound = bind { it.copy(packageName = "com.exemplo.outro") }

        verifier.verify(bound.device).shouldBeNull()
    }

    @Test
    fun `política recusa app assinado por outro certificado`() = runTest {
        val bound = bind { it.copy(signatureDigest = TestChains.OTHER_DIGEST) }

        verifier.verify(bound.device).shouldBeNull()
    }

    @Test
    fun `política recusa chave fora do hardware seguro`() = runTest {
        val bound = bind {
            it.copy(attestationSecurityLevel = com.android.keyattestation.verifier.SecurityLevel.SOFTWARE)
        }

        verifier.verify(bound.device).shouldBeNull()
    }

    @Test
    fun `chave do aparelho diferente da chave da folha é adulterado`() = runTest {
        val bound = bind()
        val otherKey = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        val device = bound.device.copy(publicKey = TestChains.uncompressed(otherKey.public))

        verifier.verify(device).shouldBeNull()
    }

    @Test
    fun `validade conferida no vínculo - certificado RKP já vencido hoje continua aceito`() = runTest {
        val expiresAt = REGISTERED_AT.plus(Duration.ofDays(30))
        val bound = bind { it.copy(intermediateNotAfter = expiresAt) }
        // Conferida hoje, a mesma cadeia seria recusada: o instante de referência é que a mantém válida
        val today = ExpectedAttestation(
            config.chainId,
            CONTRACT,
            OWNER,
            bound.device.publicKey,
            config.allowedCertDigests,
            referenceTime = expiresAt.plus(Duration.ofDays(30)),
            window = ChallengeWindow.VERIFICATION
        )
        AttestationPolicy(trustAnchors = { TestChains.anchors }).evaluate(bound.chain, today) shouldBe
            AttestationVerdict.Rejected(RejectReason.CHAIN_INVALID)

        verifier.verify(bound.device) shouldBe TestAttestation.origin(bound.device)
    }

    @Test
    fun `desafio emitido 29 min antes do vínculo é aceito`() = runTest {
        val bound = bind(issuedAt = REGISTERED_AT.minus(Duration.ofMinutes(29)))

        verifier.verify(bound.device) shouldBe TestAttestation.origin(bound.device)
    }

    @Test
    fun `desafio emitido 31 min antes do vínculo é recusado`() = runTest {
        val bound = bind(issuedAt = REGISTERED_AT.minus(Duration.ofMinutes(31)))

        verifier.verify(bound.device).shouldBeNull()
    }

    @Test
    fun `desafio de outra carteira que não a dona do aparelho é recusado`() = runTest {
        val bound = bind(challengeWallet = OTHER_WALLET)

        verifier.verify(bound.device).shouldBeNull()
    }

    @Test
    fun `revogação depois do vínculo não muda a aceitação, só marca a origem`() = runTest {
        val bound = bind()
        coEvery { revocation.status(bound.serials) } returns RevocationStatus.REVOKED_AFTER_BINDING

        verifier.verify(bound.device) shouldBe
            TestAttestation.origin(bound.device, RevocationStatus.REVOKED_AFTER_BINDING)
    }

    @Test
    fun `revogação que não pôde ser consultada fica UNKNOWN e o aparelho continua aceito`() = runTest {
        val bound = bind()
        coEvery { revocation.status(any()) } returns RevocationStatus.UNKNOWN

        verifier.verify(bound.device) shouldBe TestAttestation.origin(bound.device, RevocationStatus.UNKNOWN)
    }

    @Test
    fun `a cadeia de cada aparelho é lida uma vez por sessão e a revogação a cada consulta`() = runTest {
        val bound = bind()

        verifier.verify(bound.device)
        verifier.verify(bound.device)

        coVerify(exactly = 1) { reader.getAttestationChain(DEVICE_ID, bound.device.registeredBlock) }
        coVerify(exactly = 2) { revocation.status(bound.serials) }
    }

    @Test
    fun `atestação inválida também fica guardada na sessão`() = runTest {
        val bound = bind { it.copy(packageName = "com.exemplo.outro") }

        verifier.verify(bound.device).shouldBeNull()
        verifier.verify(bound.device).shouldBeNull()

        coVerify(exactly = 1) { reader.getAttestationChain(DEVICE_ID, bound.device.registeredBlock) }
    }

    @Test
    fun `erro de rede ao ler o evento sobe como erro de rede e não fica guardado`() = runTest {
        val bound = TestAttestation.bind(config, id = DEVICE_ID, owner = OWNER, key = key, registeredAt = REGISTERED_AT)
        coEvery { reader.getAttestationChain(DEVICE_ID, bound.device.registeredBlock) } throws
            RegistryException.Network(IOException("sem rede")) andThen bound.chain

        shouldThrow<RegistryException.Network> { verifier.verify(bound.device) }
        verifier.verify(bound.device) shouldBe TestAttestation.origin(bound.device)
    }

    private companion object {
        const val CONTRACT = "0xfBa7aeDa5e979F88336b35e81A1780b4655BAe6E"
        const val OWNER = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val OTHER_WALLET = "0xABCDEFabcdef0123456789ABCDEFabcdef012345"
        const val DEVICE_ID = 7
        val REGISTERED_AT: Instant = Instant.parse("2026-10-06T21:00:00Z")
    }
}
