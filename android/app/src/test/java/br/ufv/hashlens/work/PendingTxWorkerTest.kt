package br.ufv.hashlens.work

import android.app.Application
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.ReceiptTracker
import br.ufv.hashlens.data.chain.RegistryException
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.TxReceipt
import br.ufv.hashlens.data.local.AppDatabase
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.domain.model.Sha256
import br.ufv.hashlens.testing.inMemoryDatabase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * T044: acompanhamento de transações pendentes e prazo de confirmação (data-model §2.3, FR-014,
 * research R23). Nenhum registro vai a `FAILED` sem antes reconciliar por `getIdBySha256`: o hash é
 * a identidade do registro, não o `txHash`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingTxWorkerTest {
    private val db: AppDatabase = inMemoryDatabase()
    private val images = db.localImageDao()
    private val receipts = mockk<ReceiptTracker>()
    private val reader = mockk<RegistryReader>()
    private val defaultConfig = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")

    @After
    fun closeDatabase() = db.close()

    /** Roda um ciclo do worker com o relógio em `SUBMITTED_AT + elapsed`. */
    private suspend fun runWorker(elapsed: Duration, config: ChainConfig = defaultConfig): ListenableWorker.Result {
        val clock = Clock.fixed(SUBMITTED_AT.plus(elapsed), ZoneOffset.UTC)
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ) = PendingTxWorker(appContext, workerParameters, images, receipts, reader, config, clock)
        }
        return TestListenableWorkerBuilder<PendingTxWorker>(RuntimeEnvironment.getApplication())
            .setWorkerFactory(factory)
            .build()
            .doWork()
    }

    private suspend fun insert(
        status: RegistrationStatus = RegistrationStatus.PENDING,
        failureReason: FailureReason? = null
    ): Long = images.insert(
        LocalImageEntity(
            contentUri = "content://media/external/images/media/42",
            sha256Hex = SHA.hex,
            pHashHex = "fe44819b7ec4c03b",
            origin = ImageOrigin.CAPTURE,
            walletAddress = WALLET,
            deviceId = 3,
            signatureHex = "ab".repeat(64),
            status = status,
            txHash = TX,
            submittedAt = SUBMITTED_AT.toEpochMilli(),
            failureReason = failureReason,
            createdAt = SUBMITTED_AT.toEpochMilli()
        )
    )

    private suspend fun image(id: Long): LocalImageEntity = images.getById(id).shouldNotBeNull()

    private fun receipt(value: TxReceipt?) = coEvery { receipts.receipt(TX) } returns value

    private fun onChainId(value: Long?) = coEvery { reader.getIdBySha256(SHA) } returns value

    private fun LocalImageEntity.shouldBeConfirmed(recordId: Long) {
        status shouldBe RegistrationStatus.CONFIRMED
        this.recordId shouldBe recordId
        failureReason.shouldBeNull()
    }

    private fun LocalImageEntity.shouldBePending() {
        status shouldBe RegistrationStatus.PENDING
        recordId.shouldBeNull()
        failureReason.shouldBeNull()
    }

    @Test
    fun `recibo com status 1 confirma com o recordId do evento`() = runTest {
        val id = insert()
        receipt(TxReceipt(success = true, recordId = 7))

        runWorker(Duration.ofMinutes(1)) shouldBe ListenableWorker.Result.success()

        image(id).shouldBeConfirmed(recordId = 7)
    }

    @Test
    fun `recibo com status 0 e hash fora da rede falha por REVERTED`() = runTest {
        val id = insert()
        receipt(TxReceipt(success = false))
        onChainId(null)

        runWorker(Duration.ofMinutes(1))

        image(id).run {
            status shouldBe RegistrationStatus.FAILED
            failureReason shouldBe FailureReason.REVERTED
        }
    }

    @Test
    fun `recibo com status 0 mas hash já na rede confirma com o id on-chain`() = runTest {
        // A transação antiga foi confirmada depois do reenvio; a nova reverteu com AlreadyRegistered
        val id = insert()
        receipt(TxReceipt(success = false))
        onChainId(9)

        runWorker(Duration.ofMinutes(1))

        image(id).shouldBeConfirmed(recordId = 9)
    }

    @Test
    fun `30 min sem recibo e hash fora da rede falha por TIMEOUT`() = runTest {
        val id = insert()
        receipt(null)
        onChainId(null)

        runWorker(Duration.ofMinutes(30))

        image(id).run {
            status shouldBe RegistrationStatus.FAILED
            failureReason shouldBe FailureReason.TIMEOUT
        }
    }

    @Test
    fun `30 min sem recibo mas hash já na rede confirma`() = runTest {
        val id = insert()
        receipt(null)
        onChainId(9)

        runWorker(Duration.ofMinutes(30))

        image(id).shouldBeConfirmed(recordId = 9)
    }

    @Test
    fun `29 min sem recibo continua PENDING sem consultar o hash`() = runTest {
        val id = insert()
        receipt(null)

        runWorker(Duration.ofMinutes(29))

        image(id).shouldBePending()
        coVerify(exactly = 0) { reader.getIdBySha256(any()) }
    }

    @Test
    fun `erro de rede na reconciliação mantém PENDING`() = runTest {
        val id = insert()
        receipt(null)
        coEvery { reader.getIdBySha256(SHA) } throws RegistryException.Network(IOException("sem conexão"))

        runWorker(Duration.ofMinutes(31)) shouldBe ListenableWorker.Result.success()

        image(id).shouldBePending()
    }

    @Test
    fun `erro de rede ao buscar o recibo mantém PENDING`() = runTest {
        val id = insert()
        coEvery { receipts.receipt(TX) } throws RegistryException.Network(IOException("sem conexão"))

        runWorker(Duration.ofMinutes(31)) shouldBe ListenableWorker.Result.success()

        image(id).shouldBePending()
    }

    @Test
    fun `registro em FAILED(TIMEOUT) é reconciliado num ciclo posterior`() = runTest {
        val id = insert(status = RegistrationStatus.FAILED, failureReason = FailureReason.TIMEOUT)
        receipt(null)
        onChainId(9)

        runWorker(Duration.ofHours(2))

        image(id).shouldBeConfirmed(recordId = 9)
    }

    @Test
    fun `falhas que não são por prazo ficam como estão`() = runTest {
        val id = insert(status = RegistrationStatus.FAILED, failureReason = FailureReason.REJECTED)

        runWorker(Duration.ofHours(2))

        image(id).run {
            status shouldBe RegistrationStatus.FAILED
            failureReason shouldBe FailureReason.REJECTED
        }
        coVerify(exactly = 0) { receipts.receipt(any()) }
        coVerify(exactly = 0) { reader.getIdBySha256(any()) }
    }

    @Test
    fun `prazo vem do ChainConfig`() = runTest {
        val shortTimeout = defaultConfig.copy(pendingTimeout = Duration.ofMinutes(10))
        val pendingId = insert()
        receipt(null)
        onChainId(null)

        runWorker(Duration.ofMinutes(10), defaultConfig)
        image(pendingId).shouldBePending()

        runWorker(Duration.ofMinutes(10), shortTimeout)
        image(pendingId).run {
            status shouldBe RegistrationStatus.FAILED
            failureReason shouldBe FailureReason.TIMEOUT
        }
    }

    private companion object {
        const val CONTRACT = "0xF2f42B34414936e456c6AC10c4844f456CAD65Ba"
        const val WALLET = "0x1234567890abcdef1234567890abcdef12345678"
        const val TX = "0x4444444444444444444444444444444444444444444444444444444444444444"
        val SHA = Sha256("55824746f88c258e4ef10a10027ba7751e85b67c866f63023bdf90c2207aa527")
        val SUBMITTED_AT: Instant = Instant.parse("2026-10-09T18:00:00Z")
    }
}
