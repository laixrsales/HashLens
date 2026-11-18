package br.ufv.hashlens.attestation

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.config.ChainConfig
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 020/T016: captura a cadeia de atestação real deste aparelho para os testes da política
 * (`:attestation`, T020). Gera uma chave como o app gera (P-256, só assinatura, SHA-256), com o
 * desafio de `contracts/attestation-policy.md` §2 para o contrato de desenvolvimento e uma carteira
 * fixa de teste, e escreve cadeia, desafio e dados do aparelho em JSON no logcat (tag
 * `AttestationT016`, em pedaços `<arquivo> <n>/<total> <texto>`), porque o AGP desinstala o app ao
 * fim dos testes e apaga os arquivos dele. Para remontar em
 * `android/attestation/src/test/resources/attestation/real/`:
 *
 * ```
 * adb logcat -c   # antes de rodar o teste
 * adb logcat -d -s AttestationT016:I | python3 tools/attestation/remontar_cadeia.py
 * ```
 *
 * Gera uma chave no TEE e, se o aparelho tiver, outra no StrongBox. Não altera as chaves do app.
 */
@RunWith(AndroidJUnit4::class)
class CaptureReferenceChainTest {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteKeys() {
        listOf(ALIAS_TEE, ALIAS_STRONGBOX).filter(keyStore::containsAlias).forEach(keyStore::deleteEntry)
    }

    @Test
    fun capturaACadeiaDeReferencia() {
        val name = Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "_")
        capture(ALIAS_TEE, strongBox = false, "$name-tee.json")

        val hasStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        Log.i(TAG, "StrongBox disponível: $hasStrongBox")
        if (hasStrongBox) {
            capture(ALIAS_STRONGBOX, strongBox = true, "$name-strongbox.json")
        }
    }

    private fun capture(alias: String, strongBox: Boolean, fileName: String) {
        val config = ChainConfig.fromBuildConfig()
        val issuedAt = Instant.ofEpochSecond(Instant.now().epochSecond)
        val challenge = AttestationChallenge(config.chainId, config.registryAddress, WALLET, issuedAt)

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAttestationChallenge(challenge.encode())
                    .apply { if (strongBox) setIsStrongBoxBacked(true) }
                    .build()
            )
            generateKeyPair()
        }
        val chain = keyStore.getCertificateChain(alias).map { it.encoded }
        chain shouldHaveAtLeastSize 2
        val publicKey = keyStore.getCertificate(alias).publicKey as ECPublicKey

        val json = JSONObject()
            .put("capturedAt", issuedAt.epochSecond)
            .put("chainId", config.chainId)
            .put("contract", challenge.contract)
            .put("wallet", challenge.wallet)
            .put("challenge", hex(challenge.encode()))
            .put("publicKey", hex(uncompressed(publicKey)))
            .put("strongBox", strongBox)
            .put(
                "device",
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("release", Build.VERSION.RELEASE)
                    .put("securityPatch", Build.VERSION.SECURITY_PATCH)
            )
            .put("chain", JSONArray(chain.map { Base64.encodeToString(it, Base64.NO_WRAP) }))
        val parts = json.toString().chunked(LOG_CHUNK)
        parts.forEachIndexed { i, part -> Log.i(TAG, "$fileName ${i + 1}/${parts.size} $part") }
        Log.i(TAG, "cadeia com ${chain.size} certificados escrita em $fileName")
    }

    private fun uncompressed(key: ECPublicKey): ByteArray =
        byteArrayOf(0x04) + fixed(key.w.affineX) + fixed(key.w.affineY)

    private fun fixed(value: BigInteger): ByteArray {
        val magnitude = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(COORDINATE_SIZE - magnitude.size) + magnitude
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS_TEE = "hashlens-t016-tee"
        const val ALIAS_STRONGBOX = "hashlens-t016-strongbox"
        const val COORDINATE_SIZE = 32
        const val LOG_CHUNK = 1000
        const val TAG = "AttestationT016"

        /** Carteira fixa de teste (a mesma dos vetores da 002 e do EIP-712). */
        const val WALLET = "0x1234567890AbcdEF1234567890aBcdef12345678"
    }
}
