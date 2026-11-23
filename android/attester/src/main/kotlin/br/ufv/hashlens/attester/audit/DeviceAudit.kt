package br.ufv.hashlens.attester.audit

import br.ufv.hashlens.attestation.AttestationPolicy
import br.ufv.hashlens.attestation.AttestationVerdict
import br.ufv.hashlens.attestation.ChallengeWindow
import br.ufv.hashlens.attestation.ExpectedAttestation
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import org.web3j.crypto.Hash

/** Dados públicos do vínculo, lidos de `getDevice` (020 data-model §1). */
class AuditedDevice(
    val id: Int,
    val owner: String,
    val publicKey: ByteArray,
    val registeredAt: Instant,
    val attestationHash: ByteArray,
    val registeredBlock: Long
)

/** Resultado da auditoria de um aparelho (SC-006). */
sealed interface AuditReport {
    /** `getDevice` devolveu id 0: o aparelho não existe neste contrato. */
    data class NotFound(val deviceId: Int) : AuditReport

    /** O bloco do vínculo não tem o evento `DeviceAttested` do aparelho. */
    data class EventMissing(val device: AuditedDevice) : AuditReport

    /** O hash da cadeia do evento não é o gravado no aparelho. */
    data class HashMismatch(val device: AuditedDevice, val computed: ByteArray) : AuditReport

    /**
     * A política foi aplicada à cadeia publicada.
     *
     * @property revokedNow algum certificado da cadeia está hoje na lista do Google; `null` se a lista
     *   não pôde ser consultada. Como na verificação do app, não muda o veredito (FR-017).
     */
    data class Evaluated(
        val device: AuditedDevice,
        val chain: List<ByteArray>,
        val verdict: AttestationVerdict,
        val revokedNow: Boolean?
    ) : AuditReport
}

/**
 * Auditoria independente de um vínculo (020 SC-006, T056): lê `getDevice` e o evento
 * `DeviceAttested` por RPC, confere `keccak256(abi.encode(chain)) == attestationHash` e aplica a
 * mesma [AttestationPolicy] do atestador, sem a chave dele e sem o app.
 *
 * A política roda como na verificação do app: validade dos certificados em `registeredAt`, carteira
 * do desafio = dona do aparelho e janela [ChallengeWindow.VERIFICATION]. A revogação é consultada à
 * parte e só informada.
 *
 * @param revokedSerials seriais revogados ou suspensos (hex minúsculo); `null` se a lista falhou.
 */
class DeviceAudit(
    private val rpc: JsonRpc,
    private val chainId: Long,
    private val contract: String,
    private val allowedCertDigests: Set<String>,
    private val policy: AttestationPolicy = AttestationPolicy(),
    private val revokedSerials: () -> Set<String>? = { null }
) {
    fun audit(deviceId: Int): AuditReport {
        val device = Abi.decodeDevice(rpc.ethCall(contract, Abi.getDeviceCall(deviceId)))
            ?: return AuditReport.NotFound(deviceId)
        val chain = rpc.getLogs(
            contract,
            listOf(Abi.DEVICE_ATTESTED_TOPIC, Abi.word(BigInteger.valueOf(deviceId.toLong()))),
            device.registeredBlock
        ).firstNotNullOfOrNull { Abi.decodeChain(it) }
        val computed = chain?.let { Hash.sha3(Abi.encodeBytesArray(it)) }
        return when {
            chain == null || computed == null -> AuditReport.EventMissing(device)

            !computed.contentEquals(device.attestationHash) -> AuditReport.HashMismatch(device, computed)

            else -> AuditReport.Evaluated(device, chain, evaluate(device, chain), revokedNow(chain))
        }
    }

    private fun evaluate(device: AuditedDevice, chain: List<ByteArray>): AttestationVerdict = policy.evaluate(
        chain,
        ExpectedAttestation(
            chainId = chainId,
            contract = contract,
            wallet = device.owner,
            publicKey = device.publicKey,
            allowedCertDigests = allowedCertDigests,
            referenceTime = device.registeredAt,
            window = ChallengeWindow.VERIFICATION
        )
    )

    /** Seriais da cadeia, em hex e em decimal, como na política (a lista do Google mistura as duas formas). */
    private fun revokedNow(chain: List<ByteArray>): Boolean? {
        val revoked = revokedSerials() ?: return null
        return certificateSerials(chain).any { serial ->
            serial.toString(HEX_RADIX) in revoked || serial.toString() in revoked
        }
    }

    /** Seriais dos certificados da cadeia (o que não decodifica fica de fora). */
    private fun certificateSerials(chain: List<ByteArray>): List<BigInteger> {
        val factory = CertificateFactory.getInstance("X.509")
        return chain.mapNotNull { der ->
            runCatching { (factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate).serialNumber }
                .getOrNull()
        }
    }

    private companion object {
        const val HEX_RADIX = 16
    }
}
