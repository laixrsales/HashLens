package br.ufv.hashlens.data.attestation

import br.ufv.hashlens.attestation.AttestationHash
import br.ufv.hashlens.attestation.AttestationPolicy
import br.ufv.hashlens.attestation.AttestationVerdict
import br.ufv.hashlens.attestation.ChallengeWindow
import br.ufv.hashlens.attestation.ExpectedAttestation
import br.ufv.hashlens.attestation.SecurityLevel as PolicySecurityLevel
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.domain.model.BootState
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.DeviceOrigin
import br.ufv.hashlens.domain.model.SecurityLevel
import java.util.concurrent.ConcurrentHashMap

/**
 * Revalida, na verificação, a atestação do aparelho de um registro (020 research R12,
 * `contracts/verification-result.md` §1, `contracts/attestation-policy.md` §5):
 *
 * 1. lê a cadeia do evento `DeviceAttested` no bloco do vínculo;
 * 2. confere `keccak256(abi.encode(chain)) == device.attestationHash`;
 * 3. aplica a mesma [AttestationPolicy] do atestador, com `referenceTime = device.registeredAt`
 *    (certificados RKP vencem depois do vínculo), a carteira do desafio igual a `device.owner` e a
 *    janela [ChallengeWindow.VERIFICATION];
 * 4. consulta a revogação à parte, sem reprovar (FR-017).
 *
 * Os passos 1 a 3 ficam guardados por `deviceId` enquanto o app está aberto (a cadeia gravada não
 * muda); a revogação segue o cache HTTP do [RevocationRepository]. Erro de rede no passo 1 sobe
 * como `RegistryException.Network` e não é guardado.
 *
 * @param policy política com as raízes do Google; nos testes, a raiz de teste.
 */
class DeviceOriginVerifier(
    private val reader: RegistryReader,
    private val config: ChainConfig,
    private val revocation: RevocationRepository,
    private val policy: AttestationPolicy = AttestationPolicy()
) {
    /** Resultado dos passos 1 a 3; `accepted == null` é atestação inválida. */
    private class Validation(val accepted: AttestationVerdict.Accepted?)

    private val validated = ConcurrentHashMap<Int, Validation>()

    /** Origem do aparelho, ou `null` se a atestação não confere (o registro é "adulterado"). */
    suspend fun verify(device: Device): DeviceOrigin? {
        val validation = validated[device.id] ?: Validation(validate(device)).also { validated[device.id] = it }
        val accepted = validation.accepted ?: return null
        return DeviceOrigin(
            securityLevel = when (accepted.securityLevel) {
                PolicySecurityLevel.TEE -> SecurityLevel.TEE
                PolicySecurityLevel.STRONGBOX -> SecurityLevel.STRONGBOX
            },
            // A política só aceita sistema verificado (regra 4)
            bootState = BootState.VERIFIED,
            osPatchLevel = accepted.osPatchLevel,
            boundAt = device.registeredAt,
            revocation = revocation.status(accepted.certSerials)
        )
    }

    private suspend fun validate(device: Device): AttestationVerdict.Accepted? {
        val chain = reader.getAttestationChain(device.id, device.registeredBlock)
        if (chain == null || !AttestationHash.of(chain).contentEquals(device.attestationHash)) return null
        val expected = ExpectedAttestation(
            chainId = config.chainId,
            contract = config.registryAddress,
            wallet = device.owner,
            publicKey = device.publicKey,
            allowedCertDigests = config.allowedCertDigests,
            referenceTime = device.registeredAt,
            window = ChallengeWindow.VERIFICATION
        )
        return try {
            policy.evaluate(chain, expected) as? AttestationVerdict.Accepted
        } catch (_: IllegalArgumentException) {
            // Endereço fora do formato vindo da rede: os dados não são confiáveis
            null
        }
    }
}
