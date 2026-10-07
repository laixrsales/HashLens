package br.ufv.hashlens.data.chain

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.testing.ChainResponses
import br.ufv.hashlens.testing.SignatureVectors
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** T027: decodificação das respostas `view` do ImageRegistry, capturadas com `cast`. */
class AbiMapperTest {
    private val original = SignatureVectors.case("original")
    private val edition = SignatureVectors.case("edicao")

    @Test
    fun `getRecord de uma captura original`() {
        AbiMapper.decodeRecord(ChainResponses.response("getRecord 1")) shouldBe ImageRecord(
            id = 1L,
            sha256 = original.payload.sha256,
            pHash = original.payload.pHash,
            parentId = null,
            originalId = 1L,
            registrant = ChainResponses.wallet,
            deviceId = 1,
            signature = original.rawHex.hexToByteArray(),
            timestamp = ChainResponses.captureTime,
            operations = ""
        )
    }

    @Test
    fun `getRecord de uma edição`() {
        AbiMapper.decodeRecord(ChainResponses.response("getRecord 2")) shouldBe ImageRecord(
            id = 2L,
            sha256 = edition.payload.sha256,
            pHash = edition.payload.pHash,
            parentId = 1L,
            originalId = 1L,
            registrant = ChainResponses.wallet,
            deviceId = 1,
            signature = edition.rawHex.hexToByteArray(),
            timestamp = ChainResponses.editTime,
            operations = "brightness:+15;grayscale"
        )
    }

    @Test
    fun `getRecord inexistente (id 0) vira null`() {
        AbiMapper.decodeRecord(ChainResponses.response("getRecord inexistente")).shouldBeNull()
    }

    @Test
    fun `getDevice existente`() {
        AbiMapper.decodeDevice(ChainResponses.response("getDevice 1")) shouldBe Device(
            id = 1,
            owner = ChainResponses.wallet,
            publicKey = SignatureVectors.publicKeyBytes
        )
    }

    @Test
    fun `getDevice inexistente (id 0) vira null`() {
        AbiMapper.decodeDevice(ChainResponses.response("getDevice inexistente")).shouldBeNull()
    }

    @Test
    fun `getDevicesOf com e sem aparelhos`() {
        AbiMapper.decodeDeviceIds(ChainResponses.response("getDevicesOf carteira")) shouldBe listOf(1)
        AbiMapper.decodeDeviceIds(ChainResponses.response("getDevicesOf sem aparelhos")).shouldBeEmpty()
    }

    @Test
    fun `getIdBySha256 encontrado e não encontrado`() {
        AbiMapper.decodeId(ChainResponses.response("getIdBySha256 original")) shouldBe 1L
        AbiMapper.decodeId(ChainResponses.response("getIdBySha256 inexistente")) shouldBe 0L
    }

    @Test
    fun `getIdsByPHash, getChildren e totalRecords`() {
        AbiMapper.decodeIds(ChainResponses.response("getIdsByPHash compartilhado")) shouldBe listOf(1L, 2L)
        AbiMapper.decodeIds(ChainResponses.response("getIdsByPHash inexistente")).shouldBeEmpty()
        AbiMapper.decodeIds(ChainResponses.response("getChildren 1")) shouldBe listOf(2L)
        AbiMapper.decodeIds(ChainResponses.response("getChildren 2")).shouldBeEmpty()
        AbiMapper.decodeId(ChainResponses.response("totalRecords")) shouldBe 2L
    }

    @Test
    fun `registros decodificados passam na verificação da assinatura do aparelho`() {
        val config = ChainConfig(rpcUrl = "", registryAddress = ChainResponses.contract, reownProjectId = "")
        val verifier = DeviceSignatureVerifier(config)
        val device = requireNotNull(AbiMapper.decodeDevice(ChainResponses.response("getDevice 1")))

        listOf("getRecord 1", "getRecord 2").forEach { name ->
            val record = requireNotNull(AbiMapper.decodeRecord(ChainResponses.response(name)))
            verifier.verify(record, device) shouldBe true
        }
    }

    @Test
    fun `calldata de cada chamada é igual ao do cast`() {
        val expected = mapOf(
            "getRecord 1" to AbiMapper.getRecord(1L),
            "getDevice 1" to AbiMapper.getDevice(1),
            "getDevicesOf carteira" to AbiMapper.getDevicesOf(ChainResponses.wallet),
            "getIdBySha256 original" to AbiMapper.getIdBySha256(original.payload.sha256),
            "getIdsByPHash compartilhado" to AbiMapper.getIdsByPHash(original.payload.pHash),
            "getChildren 1" to AbiMapper.getChildren(1L),
            "totalRecords" to AbiMapper.totalRecords()
        )

        expected.forEach { (name, calldata) -> calldata shouldBe ChainResponses.call(name).calldata }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "0x", "0x00", "0xzz"])
    fun `resposta malformada é rejeitada`(response: String) {
        shouldThrow<IllegalArgumentException> { AbiMapper.decodeRecord(response) }
        shouldThrow<IllegalArgumentException> { AbiMapper.decodeDevice(response) }
        shouldThrow<IllegalArgumentException> { AbiMapper.decodeIds(response) }
        shouldThrow<IllegalArgumentException> { AbiMapper.decodeId(response) }
    }

    @Test
    fun `resposta truncada é rejeitada`() {
        val truncated = ChainResponses.response("getRecord 1").dropLast(64)

        shouldThrow<IllegalArgumentException> { AbiMapper.decodeRecord(truncated) }
    }
}
