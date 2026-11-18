package br.ufv.hashlens.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chave do aparelho no Android Keystore: um par P-256 por carteira, alias
 * `hashlens-device-<endereço em minúsculas>` (research R4).
 *
 * A chave privada não é exportável e só assina (`PURPOSE_SIGN`, SHA-256), em StrongBox quando o
 * aparelho tem e no TEE caso contrário (Constituição III). Se o alias some (dados do app apagados),
 * [getOrCreatePublicKey] gera outra chave; não há recuperação, e o vínculo novo é feito pelo
 * `EnsureDeviceBoundUseCase` (research R25).
 *
 * Para o vínculo com o atestador (020 research R11), [regenerateWithChallenge] gera a chave de novo
 * com o desafio da atestação, que só pode ser definido na geração, e [getAttestationChain] devolve a
 * cadeia que o Keystore emitiu para ela. Não pede atestação de identificadores do aparelho (IMEI,
 * serial), só a da chave (research R13).
 *
 * As operações do Keystore bloqueiam: chame fora da thread principal.
 */
@Singleton
class DeviceKeyManager @Inject constructor(private val keyStore: KeyStore) {
    fun hasKey(wallet: String): Boolean = keyStore.containsAlias(alias(wallet))

    /** Chave pública `0x04 ‖ X ‖ Y` (65 bytes) do alias da carteira; gera o par se ainda não existir. */
    @Synchronized
    fun getOrCreatePublicKey(wallet: String): ByteArray {
        val alias = alias(wallet)
        if (!keyStore.containsAlias(alias)) generate(alias)
        val publicKey = keyStore.getCertificate(alias).publicKey as ECPublicKey
        return encodeUncompressed(publicKey)
    }

    /**
     * Apaga a chave da carteira e gera outra com [challenge] na atestação (020 attestation-policy
     * §2), em StrongBox quando houver e no TEE caso contrário. Só deve ser chamado quando a chave
     * atual não está vinculada no contrato, porque a anterior se perde.
     *
     * @return chave pública nova, `0x04 ‖ X ‖ Y` (65 bytes).
     */
    @Synchronized
    fun regenerateWithChallenge(wallet: String, challenge: ByteArray): ByteArray {
        val alias = alias(wallet)
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        generate(alias, challenge)
        return encodeUncompressed(keyStore.getCertificate(alias).publicKey as ECPublicKey)
    }

    /**
     * Cadeia de atestação da chave da carteira, DER da folha para a raiz, como o Keystore entrega.
     *
     * @throws IllegalStateException se a carteira ainda não tiver chave neste aparelho.
     */
    fun getAttestationChain(wallet: String): List<ByteArray> {
        val chain = keyStore.getCertificateChain(alias(wallet)) ?: error("sem chave do aparelho para a carteira")
        return chain.map { it.encoded }
    }

    /**
     * Assina [message] com `SHA256withECDSA` e devolve `r ‖ s` (64 bytes, low-S), o formato
     * gravado on-chain (signature-payload §2).
     *
     * @throws IllegalStateException se a carteira ainda não tiver chave neste aparelho.
     */
    fun sign(wallet: String, message: ByteArray): ByteArray {
        val privateKey = keyStore.getKey(alias(wallet), null) as? PrivateKey
            ?: error("sem chave do aparelho para a carteira")
        val der = Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(message)
            sign()
        }
        return SignatureCodec.derToRaw(der)
    }

    /** Apaga a chave da carteira; usado pelos testes para simular a perda (R25). */
    fun deleteKey(wallet: String) {
        val alias = alias(wallet)
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun generate(alias: String, challenge: ByteArray? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generate(alias, strongBox = true, challenge)
                return
            } catch (_: StrongBoxUnavailableException) {
                // Sem StrongBox: a chave fica no TEE
            }
        }
        generate(alias, strongBox = false, challenge)
    }

    private fun generate(alias: String, strongBox: Boolean, challenge: ByteArray?) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .apply { if (challenge != null) setAttestationChallenge(challenge) }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    private fun encodeUncompressed(key: ECPublicKey): ByteArray =
        byteArrayOf(UNCOMPRESSED_PREFIX) + toFixed(key.w.affineX) + toFixed(key.w.affineY)

    private fun toFixed(value: BigInteger): ByteArray {
        val magnitude = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(COORDINATE_SIZE - magnitude.size) + magnitude
    }

    companion object {
        private const val ALIAS_PREFIX = "hashlens-device-"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val UNCOMPRESSED_PREFIX: Byte = 0x04
        private const val COORDINATE_SIZE = 32

        /** Alias do Keystore da carteira. Mudar o formato equivale a perder as chaves (tasks.md, Notes). */
        fun alias(wallet: String): String = ALIAS_PREFIX + wallet.lowercase()
    }
}
