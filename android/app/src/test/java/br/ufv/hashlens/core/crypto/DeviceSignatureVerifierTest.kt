package br.ufv.hashlens.core.crypto

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.testing.SignatureVectors
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** T025: signature-payload §3 (reconstruir a mensagem e verificar a assinatura do aparelho). */
class DeviceSignatureVerifierTest {
    private val original = SignatureVectors.case("original")
    private val config = ChainConfig(
        rpcUrl = "",
        registryAddress = original.payload.contract,
        reownProjectId = ""
    )
    private val verifier = DeviceSignatureVerifier(config)
    private val device = Device(
        id = original.payload.deviceId,
        owner = original.payload.registrant,
        publicKey = SignatureVectors.publicKeyBytes
    )

    private fun record(case: SignatureVectors.Case) = ImageRecord(
        id = 300L,
        sha256 = case.payload.sha256,
        pHash = case.payload.pHash,
        parentId = case.payload.parentId.takeIf { it != 0L },
        originalId = 1L,
        registrant = case.payload.registrant,
        deviceId = case.payload.deviceId,
        signature = case.rawHex.hexToByteArray(),
        timestamp = Instant.parse("2026-10-06T21:00:00Z"),
        operations = case.payload.operations
    )

    @ParameterizedTest
    @ValueSource(strings = ["original", "edicao"])
    fun `assinatura válida é aceita`(name: String) {
        val case = SignatureVectors.case(name)

        verifier.verify(record(case), device.copy(id = case.payload.deviceId)) shouldBe true
    }

    @Test
    fun `assinatura com s alto também é aceita`() {
        val raw = original.rawHex.hexToByteArray()
        val s = BigInteger(1, raw.copyOfRange(32, 64))
        val highS = P256_ORDER.subtract(s).toByteArray().takeLast(32).toByteArray()

        verifier.verify(record(original).copy(signature = raw.copyOfRange(0, 32) + highS), device) shouldBe true
    }

    @Test
    fun `pHash alterado invalida a assinatura`() {
        val tampered = record(original).copy(pHash = PHash(original.payload.pHash.value xor 1uL))

        verifier.verify(tampered, device) shouldBe false
    }

    @Test
    fun `operações alteradas invalidam a assinatura`() {
        val edition = SignatureVectors.case("edicao")
        val tampered = record(edition).copy(operations = "brightness:+16;grayscale")

        verifier.verify(tampered, device.copy(id = edition.payload.deviceId)) shouldBe false
    }

    @Test
    fun `operações não canônicas são verificadas como estão, sem reserializar`() {
        // "brightness:15" decodifica igual a "brightness:+15", mas não é o texto assinado
        val edition = SignatureVectors.case("edicao")
        val rewritten = record(edition).copy(operations = "brightness:15;grayscale")

        verifier.verify(rewritten, device.copy(id = edition.payload.deviceId)) shouldBe false
    }

    @Test
    fun `assinatura de outro contrato ou outra rede é rejeitada`() {
        val otherContract = DeviceSignatureVerifier(
            config.copy(registryAddress = "0x0000000000000000000000000000000000000001")
        )
        val otherChain = DeviceSignatureVerifier(config.copy(chainId = 1L))

        otherContract.verify(record(original), device) shouldBe false
        otherChain.verify(record(original), device) shouldBe false
    }

    @Test
    fun `assinatura feita para outra carteira é rejeitada`() {
        val otherRegistrant = "0x00000000000000000000000000000000000000a1"

        verifier.verify(
            record(original).copy(registrant = otherRegistrant),
            device.copy(owner = otherRegistrant)
        ) shouldBe
            false
    }

    @Test
    fun `aparelho que não é o do registro é rejeitado`() {
        verifier.verify(record(original), device.copy(id = original.payload.deviceId + 1)) shouldBe false
    }

    @Test
    fun `aparelho de outra carteira é rejeitado`() {
        verifier.verify(record(original), device.copy(owner = "0x00000000000000000000000000000000000000a1")) shouldBe
            false
    }

    @Test
    fun `dono do aparelho é comparado sem diferenciar maiúsculas`() {
        verifier.verify(record(original), device.copy(owner = original.payload.registrant.lowercase())) shouldBe true
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 63, 65])
    fun `assinatura com tamanho diferente de 64 bytes é rejeitada`(size: Int) {
        verifier.verify(record(original).copy(signature = ByteArray(size)), device) shouldBe false
    }

    @Test
    fun `assinatura com r ou s zerado é rejeitada`() {
        verifier.verify(record(original).copy(signature = ByteArray(64)), device) shouldBe false
    }

    @Test
    fun `chave pública malformada é rejeitada sem exceção`() {
        val key = SignatureVectors.publicKeyBytes
        val compressedPrefix = key.copyOf().also { it[0] = 0x02 }
        val offCurve = key.copyOf().also { it[64] = (it[64] + 1).toByte() }

        verifier.verify(record(original), device.copy(publicKey = key.copyOf(64))) shouldBe false
        verifier.verify(record(original), device.copy(publicKey = compressedPrefix)) shouldBe false
        verifier.verify(record(original), device.copy(publicKey = offCurve)) shouldBe false
        verifier.verify(record(original), device.copy(publicKey = ByteArray(0))) shouldBe false
    }

    private companion object {
        val P256_ORDER = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
    }
}
