package br.ufv.hashlens.testing

import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.attestation.AttestationHash
import br.ufv.hashlens.attestation.AttestationPolicy
import br.ufv.hashlens.attestation.fixtures.TestChains
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.attestation.DeviceOriginVerifier
import br.ufv.hashlens.data.attestation.RevocationRepository
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.domain.model.BootState
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.DeviceOrigin
import br.ufv.hashlens.domain.model.RevocationStatus
import br.ufv.hashlens.domain.model.SecurityLevel
import java.security.KeyPair
import java.time.Instant
import java.time.YearMonth

/**
 * Aparelhos com atestação sintética para os testes da verificação (020/T049, T050): a cadeia de
 * `TestChains` (raiz de teste) com o desafio da carteira dona e a chave do próprio aparelho, e o
 * [Device] com o `attestationHash` dessa cadeia, como o contrato gravaria.
 */
object TestAttestation {
    /** Patch das cadeias sintéticas (padrão de `TestChains.Params`). */
    val PATCH: YearMonth = YearMonth.of(2026, 9)

    /** Cadeia publicada no vínculo e o [Device] correspondente. */
    class Bound(val device: Device, val chain: List<ByteArray>, val serials: List<String>)

    /**
     * @param issuedAt instante do desafio; o padrão é 1 min antes de [registeredAt].
     * @param params ajustes da cadeia (ex.: outro pacote); o desafio e a chave são sempre os daqui.
     */
    fun bind(
        config: ChainConfig,
        id: Int,
        owner: String,
        key: KeyPair,
        registeredAt: Instant = TestDevices.REGISTERED_AT,
        issuedAt: Instant = registeredAt.minusSeconds(60),
        challengeWallet: String = owner,
        registeredBlock: Long = 100L + id,
        params: (TestChains.Params) -> TestChains.Params = { it }
    ): Bound {
        val challenge = AttestationChallenge(config.chainId, config.registryAddress, challengeWallet, issuedAt).encode()
        val generated = TestChains.generate(params(TestChains.Params(challenge = challenge, leafKey = key)))
        val device = Device(
            id = id,
            owner = owner,
            publicKey = generated.publicKey,
            registeredAt = registeredAt,
            attestationHash = AttestationHash.of(generated.chain),
            registeredBlock = registeredBlock
        )
        return Bound(device, generated.chain, generated.serials)
    }

    /** Configuração com o certificado "genuíno" dos testes entre os aceitos. */
    fun config(contract: String): ChainConfig = ChainConfig(
        rpcUrl = "",
        registryAddress = contract,
        reownProjectId = "",
        allowedCertDigests = setOf(TestChains.GENUINE_DIGEST)
    )

    /** Verificador real, com a raiz de teste no lugar das raízes do Google. */
    fun verifier(reader: RegistryReader, config: ChainConfig, revocation: RevocationRepository) =
        DeviceOriginVerifier(reader, config, revocation, AttestationPolicy(trustAnchors = { TestChains.anchors }))

    /** Origem esperada de um aparelho aceito com a cadeia padrão. */
    fun origin(device: Device, revocation: RevocationStatus = RevocationStatus.NOT_REVOKED) = DeviceOrigin(
        securityLevel = SecurityLevel.TEE,
        bootState = BootState.VERIFIED,
        osPatchLevel = PATCH,
        boundAt = device.registeredAt,
        revocation = revocation
    )
}
