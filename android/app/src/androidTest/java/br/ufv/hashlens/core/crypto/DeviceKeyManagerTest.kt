package br.ufv.hashlens.core.crypto

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.attestation.AttestationPolicy
import br.ufv.hashlens.attestation.AttestationVerdict
import br.ufv.hashlens.attestation.ChallengeWindow
import br.ufv.hashlens.attestation.ExpectedAttestation
import br.ufv.hashlens.config.ChainConfig
import com.android.keyattestation.verifier.KeyDescription
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 002/T039 e 020/T027: chave do aparelho no Android Keystore (002 research R4 e R25) e a geração
 * com o desafio da atestação (020 research R11).
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
        manager.getOrCreatePublicKey(WALLET_A)
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

    // ───────────────────────────── Atestação (020/T027) ─────────────

    private val config = ChainConfig.fromBuildConfig()

    private fun challengeFor(wallet: String, issuedAt: Instant = Instant.ofEpochSecond(Instant.now().epochSecond)) =
        AttestationChallenge(config.chainId, config.registryAddress, wallet, issuedAt)

    @Test
    fun regenerarComDesafioTrocaAChaveDoAlias() {
        val before = manager.getOrCreatePublicKey(WALLET_A)

        val after = manager.regenerateWithChallenge(WALLET_A, challengeFor(WALLET_A).encode())

        after.toHexString() shouldNotBe before.toHexString()
        manager.getOrCreatePublicKey(WALLET_A).toHexString() shouldBe after.toHexString()
    }

    @Test
    fun cadeiaTemAoMenosDoisCertificadosEODesafioNaExtensaoDaFolha() {
        val challenge = challengeFor(WALLET_A)
        val publicKey = manager.regenerateWithChallenge(WALLET_A, challenge.encode())

        val chain = manager.getAttestationChain(WALLET_A)

        chain shouldHaveAtLeastSize 2
        val leaf = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(chain.first())) as X509Certificate
        val description = requireNotNull(KeyDescription.parseFrom(leaf))
        AttestationChallenge.decode(description.attestationChallenge.toByteArray()) shouldBe challenge
        // Chave pública da folha = chave do alias
        P256.decodePublicKey(publicKey).encoded.toList() shouldBe leaf.publicKey.encoded.toList()
    }

    @Test
    fun cadeiaDaChaveGeradaPeloAppPassaNaPoliticaDeAtestacao() {
        val challenge = challengeFor(WALLET_A)
        val publicKey = manager.regenerateWithChallenge(WALLET_A, challenge.encode())

        val verdict = AttestationPolicy().evaluate(
            manager.getAttestationChain(WALLET_A),
            ExpectedAttestation(
                chainId = config.chainId,
                contract = config.registryAddress,
                wallet = WALLET_A,
                publicKey = publicKey,
                // O certificado do próprio APK em execução, e não o do deploy em local.properties: com o
                // deploy de avaliação a lista só tem o release, e o teste roda com o debug
                allowedCertDigests = setOf(runningApkCertDigest()),
                referenceTime = Instant.now(),
                window = ChallengeWindow.ATTESTER
            )
        )

        verdict.shouldBeInstanceOf<AttestationVerdict.Accepted>()
    }

    @Test
    fun cadeiaSemChaveFalha() {
        shouldThrow<IllegalStateException> { manager.getAttestationChain(WALLET_B) }
    }

    /** SHA-256 (hex minúsculo) do certificado que assinou o app em teste. */
    @Suppress("DEPRECATION") // GET_SIGNATURES só abaixo do API 28
    private fun runningApkCertDigest(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val cert = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo!!.apkContentsSigners.first()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures!!.first()
        }
        return MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        // Carteiras fictícias com maiúsculas de checksum, para conferir a normalização do alias
        const val WALLET_A = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val WALLET_B = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01"
    }
}
