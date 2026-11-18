package br.ufv.hashlens.attestation.fixtures

import com.android.keyattestation.verifier.AttestationApplicationId
import com.android.keyattestation.verifier.AttestationPackageInfo
import com.android.keyattestation.verifier.AuthorizationList
import com.android.keyattestation.verifier.KeyDescription
import com.android.keyattestation.verifier.Origin
import com.android.keyattestation.verifier.PatchLevel
import com.android.keyattestation.verifier.RootOfTrust
import com.android.keyattestation.verifier.SecurityLevel
import com.android.keyattestation.verifier.VerifiedBootState
import com.google.protobuf.ByteString
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.YearMonth
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Cadeias sintéticas de atestação para os testes da política (020/T017). Uma raiz de teste
 * ([anchors], injetada no lugar das raízes do Google) assina uma intermediária, que assina a folha
 * com a extensão de atestação (OID `1.3.6.1.4.1.11129.2.1.17`) montada pelas próprias classes da
 * biblioteca, a partir de [Params]. O padrão de [Params] é uma cadeia aceita; cada variação da
 * policy §3 muda um campo (ver `AttestationPolicyTest`).
 */
object TestChains {
    const val PACKAGE = "br.ufv.hashlens"

    /** SHA-256 do certificado de assinatura "genuíno" dos testes (hex minúsculo). */
    val GENUINE_DIGEST: String = "ab".repeat(32)

    /** Outro certificado de assinatura: um APK que não é o HashLens. */
    val OTHER_DIGEST: String = "cd".repeat(32)

    // KeyMint (hardware/interfaces/security/keymint): KeyPurpose.SIGN, Algorithm.EC, EcCurve.P_256, Digest.SHA_2_256
    val PURPOSE_SIGN: BigInteger = BigInteger.valueOf(2)
    val PURPOSE_DECRYPT: BigInteger = BigInteger.valueOf(1)
    val ALGORITHM_EC: BigInteger = BigInteger.valueOf(3)
    val ALGORITHM_RSA: BigInteger = BigInteger.ONE
    val CURVE_P256: BigInteger = BigInteger.ONE
    val CURVE_P384: BigInteger = BigInteger.TWO
    val DIGEST_SHA256: BigInteger = BigInteger.valueOf(4)
    val DIGEST_NONE: BigInteger = BigInteger.ZERO

    private val notBefore = Date.from(Instant.parse("2020-01-01T00:00:00Z"))
    private val notAfter = Date.from(Instant.parse("2040-01-01T00:00:00Z"))
    private val serials = AtomicLong(1000)

    private val rootKey = ecKeyPair()
    private val rootName = X500Name("CN=HashLens Test Root")
    val root: X509Certificate = certificate(rootName, rootName, rootKey.public, rootKey, ca = true)

    /** Âncora de confiança dos testes (no lugar de `GoogleTrustAnchors`). */
    val anchors: Set<TrustAnchor> = setOf(TrustAnchor(root, null))

    private val intermediateKey = ecKeyPair()
    private val intermediateName = X500Name("CN=HashLens Test Intermediate, T=TEE")
    private val intermediate = certificate(rootName, intermediateName, intermediateKey.public, rootKey, ca = true)

    data class Params(
        val challenge: ByteArray,
        val attestationSecurityLevel: SecurityLevel = SecurityLevel.TRUSTED_ENVIRONMENT,
        val keyMintSecurityLevel: SecurityLevel = attestationSecurityLevel,
        val bootState: VerifiedBootState = VerifiedBootState.VERIFIED,
        val deviceLocked: Boolean = true,
        val osPatchLevel: YearMonth? = YearMonth.of(2026, 9),
        val packageName: String = PACKAGE,
        val signatureDigest: String = GENUINE_DIGEST,
        val purposes: Set<BigInteger> = setOf(PURPOSE_SIGN),
        val algorithm: BigInteger = ALGORITHM_EC,
        val ecCurve: BigInteger = CURVE_P256,
        val digests: Set<BigInteger> = setOf(DIGEST_SHA256),
        val origin: Origin = Origin.GENERATED,
        /** Folha assinada por uma chave fora da cadeia (assinatura inválida). */
        val leafSignedByStranger: Boolean = false,
        /** Intermediária também com extensão de atestação (só a da folha pode existir). */
        val extensionInIntermediate: Boolean = false
    )

    /** Cadeia gerada e a chave pública da folha (65 bytes, `0x04 ‖ X ‖ Y`). */
    class Generated(val chain: List<ByteArray>, val publicKey: ByteArray, val certificates: List<X509Certificate>) {
        /** Seriais em hex minúsculo, da folha para a raiz. */
        val serials: List<String> get() = certificates.map { it.serialNumber.toString(16) }
    }

    fun generate(params: Params): Generated {
        val leafKey = ecKeyPair()
        val extension = keyDescription(params).encodeToAsn1()
        val issuerKey = if (params.leafSignedByStranger) ecKeyPair() else intermediateKey
        val leaf = certificate(
            intermediateName,
            X500Name("CN=Android Keystore Key"),
            leafKey.public,
            issuerKey,
            ca = false,
            attestationExtension = extension
        )
        val middle = if (params.extensionInIntermediate) {
            certificate(
                rootName,
                intermediateName,
                intermediateKey.public,
                rootKey,
                ca = true,
                attestationExtension = extension
            )
        } else {
            intermediate
        }
        val certs = listOf(leaf, middle, root)
        return Generated(certs.map { it.encoded }, uncompressed(leafKey.public), certs)
    }

    fun uncompressed(key: PublicKey): ByteArray {
        val point = (key as ECPublicKey).w
        return byteArrayOf(0x04) + fixed(point.affineX) + fixed(point.affineY)
    }

    private fun keyDescription(p: Params): KeyDescription {
        val hardware = AuthorizationList(
            purposes = p.purposes,
            algorithms = p.algorithm,
            keySize = BigInteger.valueOf(256),
            digests = p.digests,
            ecCurve = p.ecCurve,
            noAuthRequired = true,
            origin = p.origin,
            rootOfTrust = RootOfTrust(
                verifiedBootKey = ByteString.copyFrom(ByteArray(32) { 7 }),
                deviceLocked = p.deviceLocked,
                verifiedBootState = p.bootState,
                verifiedBootHash = ByteString.copyFrom(ByteArray(32) { 9 })
            ),
            osVersion = BigInteger.valueOf(130000),
            osPatchLevel = p.osPatchLevel?.let { PatchLevel(it) },
            vendorPatchLevel = p.osPatchLevel?.let { PatchLevel(it) },
            bootPatchLevel = p.osPatchLevel?.let { PatchLevel(it) }
        )
        val software = AuthorizationList(
            creationDateTime = BigInteger.valueOf(Instant.parse("2026-11-18T12:00:00Z").toEpochMilli()),
            attestationApplicationId = AttestationApplicationId(
                packages = setOf(AttestationPackageInfo(p.packageName, BigInteger.ONE)),
                signatures = setOf(ByteString.copyFrom(bytes(p.signatureDigest)))
            )
        )
        return KeyDescription(
            attestationVersion = BigInteger.valueOf(200),
            attestationSecurityLevel = p.attestationSecurityLevel,
            keyMintVersion = BigInteger.valueOf(200),
            keyMintSecurityLevel = p.keyMintSecurityLevel,
            attestationChallenge = ByteString.copyFrom(p.challenge),
            uniqueId = ByteString.EMPTY,
            softwareEnforced = software,
            hardwareEnforced = hardware
        )
    }

    private fun certificate(
        issuer: X500Name,
        subject: X500Name,
        publicKey: PublicKey,
        signer: KeyPair,
        ca: Boolean,
        attestationExtension: ByteArray? = null
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(serials.incrementAndGet()),
            notBefore,
            notAfter,
            subject,
            publicKey
        )
        if (ca) {
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign))
        } else {
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        }
        attestationExtension?.let { builder.addExtension(KeyDescription.OID, false, it) }
        val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(signer.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(contentSigner))
    }

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun fixed(value: BigInteger): ByteArray {
        val magnitude = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(32 - magnitude.size) + magnitude
    }

    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
