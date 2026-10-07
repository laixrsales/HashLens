package br.ufv.hashlens.core.crypto

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.ImageRecord
import java.security.GeneralSecurityException
import java.security.Signature
import javax.inject.Inject

/**
 * Verificação da assinatura do aparelho de um registro (signature-payload §3).
 *
 * Inválida ou com chave malformada → `false`, que a verificação apresenta como
 * "Registro adulterado". Nunca lança exceção por dado vindo da rede.
 */
class DeviceSignatureVerifier @Inject constructor(private val config: ChainConfig) {
    fun verify(record: ImageRecord, device: Device): Boolean =
        // O contrato só aceita aparelhos da própria carteira; dados divergentes não são confiáveis
        device.id == record.deviceId &&
            device.owner.equals(record.registrant, ignoreCase = true) &&
            verifySignature(record, device.publicKey)

    private fun verifySignature(record: ImageRecord, publicKey: ByteArray): Boolean = try {
        val message = SignaturePayload(
            chainId = config.chainId,
            contract = config.registryAddress,
            registrant = record.registrant,
            deviceId = record.deviceId,
            sha256 = record.sha256,
            pHash = record.pHash,
            parentId = record.parentId ?: 0L,
            operations = record.operations
        ).toByteArray()
        Signature.getInstance(ALGORITHM).run {
            initVerify(P256.decodePublicKey(publicKey))
            update(message)
            verify(SignatureCodec.rawToDer(record.signature))
        }
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: GeneralSecurityException) {
        false
    }

    private companion object {
        const val ALGORITHM = "SHA256withECDSA"
    }
}
