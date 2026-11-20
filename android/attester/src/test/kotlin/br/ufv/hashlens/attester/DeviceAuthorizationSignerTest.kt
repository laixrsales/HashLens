package br.ufv.hashlens.attester

import com.google.gson.JsonParser
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * 020/T028: autorização EIP-712 (`contracts/device-authorization.md`) igual ao vetor gerado com
 * `cast` (T009), o mesmo que o contrato aceita no teste Foundry.
 */
class DeviceAuthorizationSignerTest {
    private val vector = JsonParser.parseString(
        javaClass.getResource("/device-authorization.json")!!.readText()
    ).asJsonObject

    private fun string(path: String): String =
        path.split(".").fold(vector as com.google.gson.JsonElement) { node, key -> node.asJsonObject[key] }.asString

    private val signer = DeviceAuthorizationSigner(
        privateKey = string("attester.privateKey"),
        chainId = vector["domain"].asJsonObject["chainId"].asLong,
        contract = string("domain.verifyingContract")
    )
    private val wallet = string("message.wallet")
    private val publicKey = hex(string("input.publicKey"))
    private val chain = vector["input"].asJsonObject["attestationChain"].asJsonArray.map { hex(it.asString) }
    private val deadline = vector["message"].asJsonObject["deadline"].asLong

    @Test
    fun `endereco do atestador e separador de dominio iguais ao vetor`() {
        signer.address shouldBe string("attester.address").lowercase()
        signer.domainSeparator.toHex() shouldBe string("domainSeparator")
    }

    @Test
    fun `structHash e digest iguais ao vetor`() {
        signer.structHash(wallet, publicKey, chain, deadline).toHex() shouldBe string("structHash")
        signer.digest(wallet, publicKey, chain, deadline).toHex() shouldBe string("digest")
    }

    @Test
    fun `assinatura igual a do cast, com v 27 ou 28 e s baixo`() {
        val signature = signer.sign(wallet, publicKey, chain, deadline)

        signature.size shouldBe 65
        signature.toHex() shouldBe string("signature")
        (signature[64].toInt() and 0xff) shouldBeInRange 27..28
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        (s <= SECP256K1_HALF_N) shouldBe true
    }

    @Test
    fun `carteira em outra caixa resulta na mesma autorizacao`() {
        signer.sign(wallet.lowercase(), publicKey, chain, deadline).toHex() shouldBe string("signature")
    }

    @Test
    fun `assinaturas de varias mensagens sao sempre low-S`() {
        repeat(50) { i ->
            val signature = signer.sign(wallet, publicKey, chain, deadline + i)
            (BigInteger(1, signature.copyOfRange(32, 64)) <= SECP256K1_HALF_N) shouldBe true
        }
    }

    private fun hex(value: String): ByteArray =
        value.removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = "0x" + joinToString("") { "%02x".format(it) }

    private companion object {
        val SECP256K1_HALF_N = BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16)
    }
}
