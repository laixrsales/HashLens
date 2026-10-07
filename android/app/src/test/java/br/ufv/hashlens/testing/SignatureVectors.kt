package br.ufv.hashlens.testing

import br.ufv.hashlens.core.crypto.SignaturePayload
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.Sha256
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/**
 * Vetores de `src/test/resources/signature/vectors.json`, gerados pela implementação de
 * referência `tools/signature/gerar_vetores.py` (chave P-256 só de teste).
 */
object SignatureVectors {
    data class Case(
        val name: String,
        val payload: SignaturePayload,
        val operationsHash: String,
        val payloadHex: String,
        val derHex: String,
        val rawHex: String,
        val highSDerHex: String
    )

    private val root: JsonNode by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/signature/vectors.json")) {
            "recurso ausente: signature/vectors.json"
        }
        stream.use { ObjectMapper().readTree(it) }
    }

    val cases: List<Case> by lazy { root["casos"].map(::toCase) }

    fun case(name: String): Case = cases.single { it.name == name }

    /** Payload do caso "original" com o último bit do pHash invertido; a assinatura não confere. */
    val tamperedPayloadHex: String get() = root["pHashAlterado"]["payload"].asText()

    /** Chave pública não comprimida `04 ‖ x ‖ y`, como gravada on-chain (65 bytes). */
    val publicKeyBytes: ByteArray get() = root["publicKey"].asText().hexToByteArray()

    val publicKey: PublicKey by lazy { decodeP256Point(publicKeyBytes) }

    fun verify(message: ByteArray, der: ByteArray): Boolean = Signature.getInstance("SHA256withECDSA").run {
        initVerify(publicKey)
        update(message)
        verify(der)
    }

    private fun toCase(node: JsonNode): Case {
        val fields = node["campos"]
        return Case(
            name = node["nome"].asText(),
            payload = SignaturePayload(
                chainId = fields["chainId"].asLong(),
                contract = fields["contract"].asText(),
                registrant = fields["registrant"].asText(),
                deviceId = fields["deviceId"].asInt(),
                sha256 = Sha256(fields["sha256"].asText()),
                pHash = PHash.fromHex(fields["pHash"].asText()),
                parentId = fields["parentId"].asLong(),
                operations = fields["operations"].asText()
            ),
            operationsHash = node["operationsHash"].asText(),
            payloadHex = node["payload"].asText(),
            derHex = node["assinaturaDer"].asText(),
            rawHex = node["assinaturaRaw"].asText(),
            highSDerHex = node["assinaturaDerSAlto"].asText()
        )
    }

    /** Ponto não comprimido `04 ‖ x ‖ y` (65 bytes) → chave pública P-256. */
    private fun decodeP256Point(point: ByteArray): PublicKey {
        require(point.size == 65 && point[0] == 0x04.toByte()) { "ponto P-256 não comprimido inválido" }
        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
        val x = BigInteger(1, point.copyOfRange(1, 33))
        val y = BigInteger(1, point.copyOfRange(33, 65))
        val spec = ECPublicKeySpec(ECPoint(x, y), params.getParameterSpec(ECParameterSpec::class.java))
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }
}
