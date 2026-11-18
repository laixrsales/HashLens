package br.ufv.hashlens.attestation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.keyattestation.verifier.GoogleTrustAnchors
import com.android.keyattestation.verifier.SecurityLevel
import com.android.keyattestation.verifier.VerificationResult
import com.android.keyattestation.verifier.Verifier
import com.android.keyattestation.verifier.challengecheckers.ChallengeMatcher
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 020/T004: a biblioteca `android/keyattestation` roda no Android (minSdk 26), não só na JVM
 * (research R4). Gera uma chave atestada no Keystore e valida a cadeia no próprio aparelho, com as
 * raízes do Google embutidas.
 *
 * Só confere que a biblioteca funciona aqui; a política do HashLens (boot, patch, pacote) é testada
 * no módulo `:attestation`. Precisa de aparelho físico: emulador não tem atestação em hardware.
 */
@RunWith(AndroidJUnit4::class)
class AttestationLibraryOnDeviceTest {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @After
    fun deleteTestKey() {
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    @Test
    fun verificaNoAparelhoACadeiaDeUmaChaveAtestada() {
        val challenge = "hashlens-t004-${System.currentTimeMillis()}".toByteArray()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAttestationChallenge(challenge)
                    .build()
            )
            generateKeyPair()
        }
        val chain = keyStore.getCertificateChain(ALIAS).map { it as X509Certificate }
        val verifier = Verifier(GoogleTrustAnchors, { emptySet() }, { Instant.now() })

        val result = verifier.verify(chain, ChallengeMatcher(challenge))

        Log.i(TAG, "cadeia com ${chain.size} certificados; resultado: $result")
        val success = result.shouldBeInstanceOf<VerificationResult.Success>()
        success.securityLevel shouldNotBe SecurityLevel.SOFTWARE
        success.publicKey.encoded.toList() shouldBe chain.first().publicKey.encoded.toList()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hashlens-t004-attestation"
        const val TAG = "AttestationT004"
    }
}
