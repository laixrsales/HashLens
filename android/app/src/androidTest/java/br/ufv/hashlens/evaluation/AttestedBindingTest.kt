package br.ufv.hashlens.evaluation

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.JpegEncoder
import br.ufv.hashlens.data.attester.AttesterClient
import br.ufv.hashlens.data.chain.ReceiptTracker
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.chain.Web3jChainRpc
import br.ufv.hashlens.data.chain.Web3jEthCall
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.AppDatabase
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.domain.usecase.CaptureAndRegisterUseCase
import br.ufv.hashlens.domain.usecase.CaptureResult
import br.ufv.hashlens.domain.usecase.EnsureDeviceBoundUseCase
import br.ufv.hashlens.domain.usecase.RegisterLocalImageUseCase
import br.ufv.hashlens.domain.usecase.RegistrationProgress
import io.kotest.assertions.withClue
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.math.BigInteger
import java.security.KeyStore
import java.time.Clock
import java.time.Duration
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager

/**
 * Vínculo atestado real no deploy de desenvolvimento (020/T040, SC-002 e SC-004).
 *
 * Roda o fluxo de captura do app ([CaptureAndRegisterUseCase] com [EnsureDeviceBoundUseCase]) sem
 * dublês na parte que importa: chave no TEE com o desafio, cadeia de atestação real, atestador real
 * (`attester.url`, em geral `http://localhost:8080` com `adb reverse tcp:8080 tcp:8080`) e o
 * contrato de `local.properties` na Sepolia. Só a carteira é trocada: em vez de abrir a MetaMask, as
 * transações são assinadas com uma chave de teste recebida pelo argumento de instrumentação
 * `walletKey` (testnet; nunca no Git nem no log). Sem o argumento, o teste é pulado.
 *
 * A chave do aparelho para a carteira é apagada antes, para que cada execução faça um vínculo novo.
 * Confere: as duas transações confirmadas, `getDevice(id)` com `attestationHash` e bloco, a cadeia
 * do evento `DeviceAttested` igual à do aparelho e o registro da foto apontando para esse
 * `deviceId`. Mede a etapa de integridade (de "Verificando a integridade" até o vínculo ir para a
 * carteira) e exige no máximo 10 s (SC-004). Grava `evaluation/vinculo-atestado.csv` e loga a
 * linha com a tag `AttestedBinding` (o arquivo some quando o Gradle desinstala o app).
 *
 * ```
 * read -s HASHLENS_TEST_WALLET_KEY && export HASHLENS_TEST_WALLET_KEY
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=br.ufv.hashlens.evaluation.AttestedBindingTest \
 *   -Pandroid.testInstrumentationRunnerArguments.walletKey="$HASHLENS_TEST_WALLET_KEY"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class AttestedBindingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val config = ChainConfig.fromBuildConfig()
    private val web3j = Web3j.build(HttpService(config.rpcUrl))
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val rpc = Web3jChainRpc(web3j)
    private val reader = RegistryReader(config, Web3jEthCall(web3j), rpc, Dispatchers.IO)
    private val receipts = ReceiptTracker(config, rpc, Dispatchers.IO)
    private lateinit var credentials: Credentials

    @Before
    fun setUp() {
        val key = InstrumentationRegistry.getArguments().getString(ARG_WALLET_KEY).orEmpty()
        assumeTrue("sem o argumento $ARG_WALLET_KEY (carteira de teste com SepoliaETH)", key.isNotBlank())
        credentials = Credentials.create(key.removePrefix("0x"))
    }

    @After
    fun tearDown() {
        database.close()
        web3j.shutdown()
    }

    @Test
    fun bindsAttestedDeviceAndRegistersCapture(): Unit = runBlocking {
        val wallet = credentials.address
        checkAttesterIsUp()
        // Chave nova para a carteira: força o vínculo mesmo se uma execução anterior já vinculou
        keyStore.deleteEntry(DeviceKeyManager.alias(wallet))
        val keyManager = DeviceKeyManager(keyStore)

        val capture = captureUseCase(keyManager)

        val timeline = mutableMapOf<String, Long>()
        val start = System.nanoTime()
        val result = capture(uniquePhoto(), rotationDegrees = 0, walletAddress = wallet) { progress ->
            val stage = progress::class.simpleName.orEmpty()
            timeline.putIfAbsent(stage, System.nanoTime())
        }
        val submitted = result.shouldBeInstanceOf<CaptureResult.Submitted>()
        val receipt = receipts.await(submitted.txHash)
        val totalMillis = (System.nanoTime() - start) / NANOS_PER_MILLI
        withClue("registro da foto ${submitted.txHash}") { receipt.success shouldBe true }

        val image = database.localImageDao().getById(submitted.localId).shouldNotBeNull()
        val deviceId = image.deviceId.shouldNotBeNull()
        val record = reader.getRecord(receipt.recordId.shouldNotBeNull()).shouldNotBeNull()
        record.deviceId shouldBe deviceId
        record.registrant.lowercase() shouldBe wallet.lowercase()

        val device = reader.getDevice(deviceId).shouldNotBeNull()
        device.publicKey.toList() shouldBe keyManager.getOrCreatePublicKey(wallet).toList()
        device.attestationHash.toList() shouldNotBe ByteArray(HASH_SIZE).toList()
        val chain = reader.getAttestationChain(deviceId, device.registeredBlock).shouldNotBeNull()
        chain.map { it.toList() } shouldBe keyManager.getAttestationChain(wallet).map { it.toList() }

        val verifying = timeline.getValue(RegistrationProgress.VerifyingDevice::class.simpleName!!)
        val binding = timeline.getValue(RegistrationProgress.BindingDevice::class.simpleName!!)
        val integrityMillis = (binding - verifying) / NANOS_PER_MILLI
        report(deviceId, device.registeredBlock, integrityMillis, totalMillis, record.id, submitted.txHash)
        withClue("etapa de integridade (SC-004)") { integrityMillis shouldBeLessThanOrEqual MAX_INTEGRITY_MILLIS }
    }

    /** O fluxo de captura do app, montado como no Hilt, com a carteira de teste. */
    private fun captureUseCase(keyManager: DeviceKeyManager): CaptureAndRegisterUseCase {
        val txBuilder = RegistryTxBuilder(config, rpc, Dispatchers.IO)
        val walletSession = testWallet()
        val attester = AttesterClient(
            config.attesterUrl,
            OkHttpClient.Builder().callTimeout(Duration.ofSeconds(ATTESTER_TIMEOUT_SECONDS)).build(),
            Dispatchers.IO
        )
        val clock = Clock.systemUTC()
        return CaptureAndRegisterUseCase(
            encoder = JpegEncoder(context),
            sha256Hasher = Sha256Hasher(),
            perceptualHasher = PerceptualHasher(),
            gallery = GalleryRepository(context, Dispatchers.IO),
            reader = reader,
            ensureDeviceBound = EnsureDeviceBoundUseCase(
                keyManager,
                attester,
                reader,
                walletSession,
                receipts,
                txBuilder,
                database.deviceBindingDao(),
                config,
                clock,
                Dispatchers.IO
            ),
            registerLocalImage = RegisterLocalImageUseCase(
                database.localImageDao(),
                keyManager,
                walletSession,
                txBuilder,
                config,
                clock,
                Dispatchers.IO
            ),
            images = database.localImageDao(),
            clock = clock
        )
    }

    private fun checkAttesterIsUp() {
        val request = Request.Builder().url(config.attesterUrl.trimEnd('/') + "/v1/health").build()
        val body = runCatching {
            OkHttpClient().newCall(request).execute().use { it.body.string() }
        }.getOrElse { throw AssertionError("atestador fora do ar em ${config.attesterUrl} (adb reverse?)", it) }
        withClue("contrato do atestador: $body") {
            body.lowercase().contains(config.registryAddress.lowercase()) shouldBe true
        }
    }

    /** Carteira conectada na Sepolia que assina e envia cada transação com a chave de teste. */
    private fun testWallet(): WalletSession {
        val manager = RawTransactionManager(web3j, credentials, config.chainId)
        return mockk {
            every { state } returns MutableStateFlow(WalletState.Connected(credentials.address, config.chainId))
            coEvery { sendTransaction(any(), any()) } coAnswers {
                val to = firstArg<String>()
                val data = secondArg<String>()
                val gas = web3j.ethEstimateGas(Transaction.createEthCallTransaction(credentials.address, to, data))
                    .send().also { check(!it.hasError()) { "estimateGas: ${it.error.message}" } }.amountUsed
                val baseFee = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                    .send().block.baseFeePerGas
                val sent = manager.sendEIP1559Transaction(
                    config.chainId,
                    PRIORITY_FEE_WEI,
                    baseFee * BigInteger.TWO + PRIORITY_FEE_WEI,
                    gas * GAS_MARGIN_PERCENT / BigInteger.valueOf(PERCENT),
                    to,
                    data,
                    BigInteger.ZERO
                )
                check(!sent.hasError()) { "envio: ${sent.error.message}" }
                sent.transactionHash.lowercase()
            }
        }
    }

    /** Foto sintética única por execução (ruído), para nunca cair em "já registrada". */
    private fun uniquePhoto(): Bitmap {
        val random = Random(System.nanoTime())
        val pixels = IntArray(PHOTO_SIDE * PHOTO_SIDE) {
            Color.rgb(random.nextInt(COLOR_LEVELS), random.nextInt(COLOR_LEVELS), random.nextInt(COLOR_LEVELS))
        }
        return Bitmap.createBitmap(pixels, PHOTO_SIDE, PHOTO_SIDE, Bitmap.Config.ARGB_8888)
    }

    @Suppress("LongParameterList")
    private fun report(
        deviceId: Int,
        block: Long,
        integrityMillis: Long,
        totalMillis: Long,
        recordId: Long,
        tx: String
    ) {
        val header = "deviceId,bloco_vinculo,integridade_ms,total_ms,registro,tx_registro"
        val line = "$deviceId,$block,$integrityMillis,$totalMillis,$recordId,$tx"
        Log.i(TAG, header)
        Log.i(TAG, line)
        val dir = File(context.getExternalFilesDir(null), "evaluation").apply { mkdirs() }
        val file = File(dir, "vinculo-atestado.csv")
        if (!file.exists()) file.writeText(header + "\n")
        file.appendText(line + "\n")
    }

    private companion object {
        const val TAG = "AttestedBinding"
        const val ARG_WALLET_KEY = "walletKey"
        const val ATTESTER_TIMEOUT_SECONDS = 30L
        const val MAX_INTEGRITY_MILLIS = 10_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val HASH_SIZE = 32
        const val PHOTO_SIDE = 640
        const val COLOR_LEVELS = 256
        const val PERCENT = 100L
        val GAS_MARGIN_PERCENT: BigInteger = BigInteger.valueOf(120)
        val PRIORITY_FEE_WEI: BigInteger = BigInteger.valueOf(1_500_000_000)
    }
}
