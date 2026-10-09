package br.ufv.hashlens.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigInteger
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T039: chave do aparelho no Android Keystore (research R4 e R25).
 *
 * Instrumentado porque o Android Keystore só existe no aparelho. Usa carteiras fictícias e apaga
 * os aliases criados ao fim de cada teste.
 */
@RunWith(AndroidJUnit4::class)
class DeviceKeyManagerTest {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val manager = DeviceKeyManager(keyStore)

    @After
    fun deleteTestKeys() {
        listOf(WALLET_A, WALLET_B).forEach { manager.deleteKey(it) }
    }

    @Test
    fun aliasUsaOEnderecoEmMinusculas() {
        DeviceKeyManager.alias(WALLET_A) shouldBe "hashlens-device-0x1234567890abcdef1234567890abcdef12345678"
    }

    @Test
    fun geraUmaChaveNoAliasDaCarteira() {
        manager.hasKey(WALLET_A) shouldBe false

        manager.getOrCreatePublicKey(WALLET_A)

        manager.hasKey(WALLET_A) shouldBe true
        keyStore.containsAlias(DeviceKeyManager.alias(WALLET_A)) shouldBe true
    }

    @Test
    fun chavePublicaTem65BytesNaoComprimidaENaCurvaP256() {
        val publicKey = manager.getOrCreatePublicKey(WALLET_A)

        publicKey.size shouldBe P256.UNCOMPRESSED_KEY_SIZE
        publicKey[0] shouldBe 0x04.toByte()
        // Lança se o ponto não estiver na curva P-256
        P256.decodePublicKey(publicKey)
    }

    @Test
    fun pedirDeNovoDevolveAMesmaChave() {
        manager.getOrCreatePublicKey(WALLET_A).toHexString() shouldBe
            manager.getOrCreatePublicKey(WALLET_A).toHexString()
    }

    @Test
    fun carteirasDiferentesTemChavesDiferentes() {
        val keyA = manager.getOrCreatePublicKey(WALLET_A)
        val keyB = manager.getOrCreatePublicKey(WALLET_B)

        keyA.toHexString() shouldNotBe keyB.toHexString()
    }

    @Test
    fun chavePrivadaNaoEExportavel() {
        manager.getOrCreatePublicKey(WALLET_A)

        val privateKey = keyStore.getKey(DeviceKeyManager.alias(WALLET_A), null) as PrivateKey
        // Chaves do Android Keystore não expõem o material: encoded é sempre nulo
        privateKey.encoded.shouldBeNull()
    }

    @Test
    fun assinaturaRsLowSVerificavelComAChavePublica() {
        val publicKey = P256.decodePublicKey(manager.getOrCreatePublicKey(WALLET_A))
        val message = "HASHLENS-IMG-v1 mensagem de teste".encodeToByteArray()

        val raw = manager.sign(WALLET_A, message)

        raw.size shouldBe SignatureCodec.RAW_SIZE
        val s = BigInteger(1, raw.copyOfRange(SignatureCodec.RAW_SIZE / 2, SignatureCodec.RAW_SIZE))
        (s <= P256.ORDER.shiftRight(1)) shouldBe true
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(message)
            verify(SignatureCodec.rawToDer(raw))
        } shouldBe true
    }

    @Test
    fun assinaturaDeOutraCarteiraNaoConfere() {
        val publicKeyB = P256.decodePublicKey(manager.getOrCreatePublicKey(WALLET_B))
        val message = "mensagem".encodeToByteArray()

        val raw = manager.sign(WALLET_A, message)

        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKeyB)
            update(message)
            verify(SignatureCodec.rawToDer(raw))
        } shouldBe false
    }

    @Test
    fun apagarOAliasEPedirDeNovoGeraChaveNova() {
        val before = manager.getOrCreatePublicKey(WALLET_A)

        // Simula a perda da chave (dados do app apagados, R25)
        manager.deleteKey(WALLET_A)
        manager.hasKey(WALLET_A) shouldBe false
        val after = manager.getOrCreatePublicKey(WALLET_A)

        after.toHexString() shouldNotBe before.toHexString()
    }

    private companion object {
        // Carteiras fictícias com maiúsculas de checksum, para conferir a normalização do alias
        const val WALLET_A = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val WALLET_B = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01"
    }
}
