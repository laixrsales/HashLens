package br.ufv.hashlens.attester.audit

import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.attestation.AttestationHash
import br.ufv.hashlens.attestation.AttestationPolicy
import br.ufv.hashlens.attestation.AttestationVerdict
import br.ufv.hashlens.attestation.RejectReason
import br.ufv.hashlens.attestation.fixtures.TestChains
import com.google.gson.JsonParser
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigInteger
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * 020/T056: auditoria independente de um vínculo (SC-006) contra um nó RPC falso, com as cadeias
 * sintéticas de `TestChains` e a mesma política do atestador.
 */
class DeviceAuditTest {
    private var deviceResponse: String = "0x" + "00".repeat(32 * 7)
    private val logs = mutableMapOf<Long, List<String>>()
    private val requests = mutableListOf<String>()

    private val rpc = JsonRpc { body ->
        requests += body
        val request = JsonParser.parseString(body).asJsonObject
        val result = when (request["method"].asString) {
            "eth_call" -> "\"$deviceResponse\""
            "eth_getLogs" -> {
                val filter = request["params"].asJsonArray[0].asJsonObject
                val block = filter["fromBlock"].asString.removePrefix("0x").toLong(16)
                logs[block].orEmpty().joinToString(prefix = "[", postfix = "]") { """{"data":"$it"}""" }
            }
            else -> error("método inesperado")
        }
        """{"jsonrpc":"2.0","id":1,"result":$result}"""
    }

    private fun audit(revoked: Set<String>? = emptySet()) = DeviceAudit(
        rpc = rpc,
        chainId = CHAIN_ID,
        contract = CONTRACT,
        allowedCertDigests = setOf(TestChains.GENUINE_DIGEST),
        policy = AttestationPolicy(trustAnchors = { TestChains.anchors }),
        revokedSerials = { revoked }
    ).audit(DEVICE_ID)

    /** Publica no contrato falso um aparelho com a cadeia [generated] e o hash [hash]. */
    private fun bind(
        generated: TestChains.Generated,
        hash: ByteArray = AttestationHash.of(generated.chain),
        publishedChain: List<ByteArray>? = generated.chain
    ) {
        deviceResponse = deviceTuple(generated.publicKey, hash)
        publishedChain?.let { logs[BLOCK] = listOf(Abi.hex(Abi.encodeBytesArray(it))) }
    }

    private fun chain(params: (TestChains.Params) -> TestChains.Params = { it }) = TestChains.generate(
        params(
            TestChains.Params(
                challenge = AttestationChallenge(CHAIN_ID, CONTRACT, OWNER, REGISTERED_AT.minusSeconds(60)).encode()
            )
        )
    )

    @Test
    fun `abi_encode da cadeia é o mesmo que o contrato e o atestador hasheiam`() {
        val generated = chain()

        org.web3j.crypto.Hash.sha3(Abi.encodeBytesArray(generated.chain)) shouldBe AttestationHash.of(generated.chain)
    }

    @Test
    fun `vínculo genuíno é aceito com os dados da atestação`() {
        val generated = chain()
        bind(generated)

        val report = audit().shouldBeInstanceOf<AuditReport.Evaluated>()

        report.verdict.shouldBeInstanceOf<AttestationVerdict.Accepted>()
        report.revokedNow shouldBe false
        report.device.owner shouldBe OWNER.lowercase()
        report.device.registeredAt shouldBe REGISTERED_AT
        report.describe().last() shouldBe "resultado: ACEITO"
    }

    @Test
    fun `lê o evento só no bloco do vínculo`() {
        bind(chain())

        audit()

        requests.single { "eth_getLogs" in it }.let {
            val filter = JsonParser.parseString(it).asJsonObject["params"].asJsonArray[0].asJsonObject
            filter["fromBlock"].asString shouldBe "0x" + BLOCK.toString(16)
            filter["toBlock"].asString shouldBe "0x" + BLOCK.toString(16)
            filter["topics"].asJsonArray[0].asString shouldBe Abi.DEVICE_ATTESTED_TOPIC
        }
    }

    @Test
    fun `cadeia de outro pacote é recusada com o motivo do atestador`() {
        bind(chain { it.copy(packageName = "com.exemplo.outro") })

        val report = audit().shouldBeInstanceOf<AuditReport.Evaluated>()

        report.verdict shouldBe AttestationVerdict.Rejected(RejectReason.APP_NOT_GENUINE)
        report.describe().last() shouldBe "resultado: RECUSADO (APP_NOT_GENUINE)"
    }

    @Test
    fun `hash gravado diferente da cadeia do evento é adulterado`() {
        bind(chain(), hash = ByteArray(32) { 1 })

        audit().shouldBeInstanceOf<AuditReport.HashMismatch>()
    }

    @Test
    fun `bloco do vínculo sem o evento é adulterado`() {
        bind(chain(), publishedChain = null)

        audit().shouldBeInstanceOf<AuditReport.EventMissing>()
    }

    @Test
    fun `aparelho inexistente`() {
        audit() shouldBe AuditReport.NotFound(DEVICE_ID)
    }

    @Test
    fun `revogação de hoje é informada sem mudar o veredito`() {
        val generated = chain()
        bind(generated)

        val report = audit(revoked = setOf(generated.serials[1])).shouldBeInstanceOf<AuditReport.Evaluated>()

        report.verdict.shouldBeInstanceOf<AttestationVerdict.Accepted>()
        report.revokedNow shouldBe true
    }

    @Test
    fun `lista de revogação indisponível fica como não consultada`() {
        bind(chain())

        audit(revoked = null).shouldBeInstanceOf<AuditReport.Evaluated>().revokedNow shouldBe null
    }

    /** Resposta ABI de `getDevice`: offset da tupla, cabeça de 6 palavras e o `bytes publicKey`. */
    private fun deviceTuple(publicKey: ByteArray, hash: ByteArray): String {
        fun word(value: BigInteger) = Abi.word(value).removePrefix("0x")
        val key = Abi.hex(publicKey).removePrefix("0x").padEnd(((publicKey.size + 31) / 32) * 64, '0')
        return "0x" +
            word(BigInteger.valueOf(32)) +
            word(BigInteger.valueOf(DEVICE_ID.toLong())) +
            word(BigInteger(OWNER.removePrefix("0x"), 16)) +
            word(BigInteger.valueOf(6L * 32)) +
            word(BigInteger.valueOf(REGISTERED_AT.epochSecond)) +
            Abi.hex(hash).removePrefix("0x") +
            word(BigInteger.valueOf(BLOCK)) +
            word(BigInteger.valueOf(publicKey.size.toLong())) +
            key
    }

    private companion object {
        const val CHAIN_ID = 11_155_111L
        const val CONTRACT = "0xfba7aeda5e979f88336b35e81a1780b4655bae6e"
        const val OWNER = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val DEVICE_ID = 5
        const val BLOCK = 12_175_100L
        val REGISTERED_AT: Instant = Instant.parse("2026-10-06T21:00:00Z")
    }
}
