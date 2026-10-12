package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.data.chain.RegistrationTxFinder
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.domain.model.EditOperationCodec
import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.domain.model.GenealogyNode
import br.ufv.hashlens.domain.model.ImageRecord
import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.domain.model.RecordView
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * T079: genealogia de um registro (verification-result §4; US5, FR-027, FR-028). Sobe por `parentId`
 * até a original (caminho) e desce por `getChildren` a partir dela (árvore), com os percentuais em
 * relação ao pai e à original e a assinatura de cada nó.
 *
 * Limite de exibição: 10 níveis (a original é o 1º) e 200 nós, percorridos em largura; acima disso
 * a árvore é cortada e sinalizada. Os nós do caminho até o registro consultado ficam sempre na
 * árvore, porque a tela mostra a linha do tempo inteira.
 *
 * Cada nó traz o link da transação que o registrou (US5, critério 3). O contrato não guarda a
 * transação, só o registro: ela vem dos eventos `ImageRegistered` da árvore e, se a consulta aos
 * eventos falhar, do acervo local para as versões registradas neste aparelho. Sem transação, o nó
 * aparece sem link, e a genealogia não falha por isso.
 *
 * O contrato é falso; a verificação da assinatura também (testada em `DeviceSignatureVerifierTest`).
 */
class TraceGenealogyUseCaseTest {
    private val reader = mockk<RegistryReader>()
    private val signatureVerifier = mockk<DeviceSignatureVerifier>()
    private val txFinder = mockk<RegistrationTxFinder>()
    private val images = mockk<LocalImageDao>()
    private val config = ChainConfig(rpcUrl = "", registryAddress = "0x0", reownProjectId = "")

    private val useCase = TraceGenealogyUseCase(
        reader = reader,
        signatureVerifier = signatureVerifier,
        txFinder = txFinder,
        images = images,
        config = config
    )

    private val records = mutableMapOf<Long, ImageRecord>()
    private val invalidSignatures = mutableSetOf<Long>()

    init {
        coEvery { reader.getRecord(any()) } answers { records[firstArg()] }
        coEvery { reader.getChildren(any()) } answers {
            val parent = firstArg<Long>()
            records.values.filter { it.parentId == parent }.map { it.id }.sorted()
        }
        // Só os aparelhos da autora e do editor existem
        coEvery { reader.getDevice(any()) } answers {
            when (val id = firstArg<Int>()) {
                AUTHOR_DEVICE -> Device(id = id, owner = AUTHOR, publicKey = ByteArray(65))
                EDITOR_DEVICE -> Device(id = id, owner = EDITOR, publicKey = ByteArray(65))
                else -> null
            }
        }
        every { signatureVerifier.verify(any(), any()) } answers { firstArg<ImageRecord>().id !in invalidSignatures }
        // Sem transações conhecidas, a não ser nos testes do link
        coEvery { txFinder.byOriginal(any()) } returns emptyMap()
        coEvery { images.getConfirmedByRecordIds(any()) } returns emptyList()
    }

    private fun confirmedLocally(recordId: Long, txHash: String) = LocalImageEntity(
        contentUri = "content://media/$recordId",
        sha256Hex = "%064x".format(recordId),
        pHashHex = ORIGINAL_PHASH.hex,
        origin = ImageOrigin.CAPTURE,
        walletAddress = AUTHOR.lowercase(),
        status = RegistrationStatus.CONFIRMED,
        txHash = txHash,
        recordId = recordId,
        createdAt = 0
    )

    private fun GenealogyNode.txUrls(): Map<Long, String?> =
        mapOf(record.id to record.txUrl) + children.flatMap { it.txUrls().entries }.associate { it.toPair() }

    private fun record(
        id: Long,
        parentId: Long? = null,
        originalId: Long = parentId?.let { records.getValue(it).originalId } ?: id,
        pHash: PHash = ORIGINAL_PHASH,
        registrant: String = AUTHOR,
        deviceId: Int = AUTHOR_DEVICE,
        operations: String = if (parentId == null) "" else "grayscale"
    ) = ImageRecord(
        id = id,
        sha256 = Sha256(MessageDigest.getInstance("SHA-256").digest("registro $id".toByteArray()).toHexString()),
        pHash = pHash,
        parentId = parentId,
        originalId = originalId,
        registrant = registrant,
        deviceId = deviceId,
        signature = ByteArray(64),
        timestamp = Instant.parse("2026-10-01T12:00:00Z").plusSeconds(id * 3_600),
        operations = operations
    ).also { records[id] = it }

    private fun view(record: ImageRecord) = RecordView(
        id = record.id,
        sha256 = record.sha256.hex,
        pHash = record.pHash.hex,
        registrant = record.registrant,
        deviceId = record.deviceId,
        timestamp = record.timestamp,
        operations = EditOperationCodec.decode(record.operations),
        txUrl = null
    )

    private suspend fun trace(id: Long): Genealogy = useCase(id).shouldBeInstanceOf<GenealogyResult.Traced>().genealogy

    /** Quantos nós a árvore tem e quantos níveis ela desce. */
    private fun GenealogyNode.count(): Int = 1 + children.sumOf { it.count() }

    private fun GenealogyNode.depth(): Int = 1 + (children.maxOfOrNull { it.depth() } ?: 0)

    @Test
    fun `versão de terceira geração mostra os três registros em ordem, da original até ela`() = runTest {
        val original = record(1, pHash = ORIGINAL_PHASH)
        val second = record(2, parentId = 1, pHash = SECOND_PHASH, registrant = EDITOR, deviceId = EDITOR_DEVICE)
        val third = record(3, parentId = 2, pHash = THIRD_PHASH, operations = "brightness:+30")

        val genealogy = trace(3)

        genealogy.pathToQueried shouldContainExactly listOf(1L, 2L, 3L)
        genealogy.truncated shouldBe false
        genealogy.root shouldBe GenealogyNode(
            record = view(original),
            percentFromParent = null,
            percentFromOriginal = 0.0,
            signatureValid = true,
            children = listOf(
                GenealogyNode(
                    record = view(second),
                    // 8 bits de diferença para a original: 12,5%
                    percentFromParent = 12.5,
                    percentFromOriginal = 12.5,
                    signatureValid = true,
                    children = listOf(
                        GenealogyNode(
                            record = view(third),
                            // 4 bits para o pai (6,25 → 6,3%) e 12 para a original (18,75 → 18,8%)
                            percentFromParent = 6.3,
                            percentFromOriginal = 18.8,
                            signatureValid = true,
                            children = emptyList()
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `original com várias versões lista todas como árvore, na ordem de registro`() = runTest {
        record(1)
        record(2, parentId = 1)
        record(3, parentId = 1, registrant = EDITOR, deviceId = EDITOR_DEVICE)
        record(4, parentId = 2)

        val genealogy = trace(1)

        genealogy.pathToQueried shouldContainExactly listOf(1L)
        genealogy.root.children.map { it.record.id } shouldContainExactly listOf(2L, 3L)
        genealogy.root.children[0].children.map { it.record.id } shouldContainExactly listOf(4L)
        genealogy.root.children[1].children shouldBe emptyList()
    }

    @Test
    fun `de uma versão do meio, a árvore é a da original inteira e o caminho só passa pelos pais`() = runTest {
        record(1)
        record(2, parentId = 1)
        record(3, parentId = 1)
        record(4, parentId = 3)

        val genealogy = trace(4)

        genealogy.pathToQueried shouldContainExactly listOf(1L, 3L, 4L)
        genealogy.root.record.id shouldBe 1L
        genealogy.root.count() shouldBe 4
    }

    @Test
    fun `responsável e aparelho de cada nó vêm do próprio registro (genealogia mista)`() = runTest {
        record(1)
        record(2, parentId = 1, registrant = EDITOR, deviceId = EDITOR_DEVICE)

        val edition = trace(2).root.children.single().record

        edition.registrant shouldBe EDITOR
        edition.deviceId shouldBe EDITOR_DEVICE
        trace(2).root.record.registrant shouldBe AUTHOR
    }

    @Test
    fun `nó com assinatura inválida fica na árvore, sinalizado`() = runTest {
        record(1)
        record(2, parentId = 1)
        record(3, parentId = 2)
        invalidSignatures += 2L

        val root = trace(3).root

        root.signatureValid shouldBe true
        root.children.single().signatureValid shouldBe false
        root.children.single().children.single().signatureValid shouldBe true
    }

    @Test
    fun `aparelho inexistente conta como assinatura inválida`() = runTest {
        record(1, deviceId = 99)

        trace(1).root.signatureValid shouldBe false
    }

    @Test
    fun `além de 10 níveis só o caminho continua, e as outras versões são cortadas`() = runTest {
        record(1)
        (2L..12L).forEach { record(it, parentId = it - 1) }
        // Uma versão fora do caminho no 12º nível
        record(13, parentId = 11)

        val genealogy = trace(12)

        genealogy.truncated shouldBe true
        genealogy.pathToQueried shouldContainExactly (1L..12L).toList()
        // A cadeia inteira até o registro consultado continua na árvore; a versão 13 fica de fora
        genealogy.root.depth() shouldBe 12
        genealogy.root.count() shouldBe 12
    }

    @Test
    fun `cadeia com exatamente 10 níveis não é cortada`() = runTest {
        record(1)
        (2L..10L).forEach { record(it, parentId = it - 1) }

        val genealogy = trace(10)

        genealogy.truncated shouldBe false
        genealogy.root.depth() shouldBe 10
    }

    @Test
    fun `mais de 200 nós corta a árvore em 200, mantendo os mais próximos da original`() = runTest {
        record(1)
        (2L..251L).forEach { record(it, parentId = 1) }

        val genealogy = trace(1)

        genealogy.truncated shouldBe true
        genealogy.root.count() shouldBe 200
        genealogy.root.children.map { it.record.id } shouldContainExactly (2L..200L).toList()
    }

    @Test
    fun `registro consultado além dos 200 nós continua na árvore`() = runTest {
        record(1)
        (2L..251L).forEach { record(it, parentId = 1) }

        val genealogy = trace(251)

        genealogy.truncated shouldBe true
        genealogy.root.count() shouldBe 200
        // O caminho entra primeiro na conta; as outras versões ocupam o espaço que sobra, em ordem
        genealogy.root.children.map { it.record.id } shouldContainExactly (2L..199L).toList() + 251L
    }

    @Test
    fun `a original tem percentual zero em relação a si mesma e nenhum em relação ao pai`() = runTest {
        record(1)

        trace(1).root.run {
            percentFromParent.shouldBeNull()
            percentFromOriginal shouldBe 0.0
        }
    }

    @Test
    fun `falha de rede é Error NETWORK`() = runTest {
        record(1)
        coEvery { reader.getChildren(any()) } throws RegistryException.Network(IOException("offline"))

        useCase(1) shouldBe GenealogyResult.Error(Reason.NETWORK)
    }

    @Test
    fun `registro inexistente ou erro do nó é Error UNKNOWN`() = runTest {
        useCase(99) shouldBe GenealogyResult.Error(Reason.UNKNOWN)

        record(1)
        coEvery { reader.getRecord(1) } throws RegistryException.Rpc(-32000, "execution reverted")
        useCase(1) shouldBe GenealogyResult.Error(Reason.UNKNOWN)
    }

    @Test
    fun `cada nó traz o link da transação, lido dos eventos da árvore`() = runTest {
        record(1)
        record(2, parentId = 1)
        record(3, parentId = 1)
        coEvery { txFinder.byOriginal(1) } returns mapOf(1L to TX_A, 2L to TX_B, 3L to TX_C)

        trace(2).root.txUrls() shouldBe mapOf(
            1L to config.transactionUrl(TX_A),
            2L to config.transactionUrl(TX_B),
            3L to config.transactionUrl(TX_C)
        )
        // Uma consulta só, pela original
        coVerify(exactly = 1) { txFinder.byOriginal(any()) }
    }

    @Test
    fun `sem os eventos, usa a transação das versões registradas neste aparelho`() = runTest {
        record(1)
        record(2, parentId = 1)
        coEvery { txFinder.byOriginal(any()) } throws
            RegistryException.Rpc(-32005, "query returned more than 10000 results")
        coEvery { images.getConfirmedByRecordIds(any()) } returns listOf(confirmedLocally(2, TX_B))

        trace(2).root.txUrls() shouldBe mapOf(1L to null, 2L to config.transactionUrl(TX_B))
    }

    @Test
    fun `os eventos prevalecem sobre o acervo local`() = runTest {
        record(1)
        coEvery { txFinder.byOriginal(1) } returns mapOf(1L to TX_A)
        coEvery { images.getConfirmedByRecordIds(any()) } returns listOf(confirmedLocally(1, TX_C))

        trace(1).root.record.txUrl shouldBe config.transactionUrl(TX_A)
    }

    @Test
    fun `falha de rede ao buscar as transações não impede a genealogia`() = runTest {
        record(1)
        record(2, parentId = 1)
        coEvery { txFinder.byOriginal(any()) } throws RegistryException.Network(IOException("offline"))

        trace(2).root.txUrls() shouldBe mapOf(1L to null, 2L to null)
    }

    private companion object {
        const val AUTHOR = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val EDITOR = "0xABCDEFabcdef0123456789ABCDEFabcdef012345"
        const val AUTHOR_DEVICE = 1
        const val EDITOR_DEVICE = 2

        val ORIGINAL_PHASH = PHash.fromHex("fe44819b7ec4c03b")

        // 8 bits trocados em relação à original; a terceira geração troca mais 4
        val SECOND_PHASH = PHash(ORIGINAL_PHASH.value xor 0xffuL)
        val THIRD_PHASH = PHash(ORIGINAL_PHASH.value xor 0xfffuL)

        const val TX_A = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val TX_B = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val TX_C = "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    }
}
