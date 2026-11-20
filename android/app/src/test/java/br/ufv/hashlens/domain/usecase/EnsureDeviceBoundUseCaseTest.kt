package br.ufv.hashlens.domain.usecase

import android.app.Application
import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.data.attester.AttesterClient
import br.ufv.hashlens.data.chain.InsufficientFundsException
import br.ufv.hashlens.data.chain.ReceiptTracker
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.chain.TransactionException
import br.ufv.hashlens.data.chain.TxCost
import br.ufv.hashlens.data.local.AppDatabase
import br.ufv.hashlens.data.local.BindingFailure
import br.ufv.hashlens.data.local.DeviceBindingEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.domain.model.AttestationRetryException
import br.ufv.hashlens.domain.model.AttesterUnavailableException
import br.ufv.hashlens.domain.model.DeviceAuthorization
import br.ufv.hashlens.domain.model.DeviceNotEligibleException
import br.ufv.hashlens.domain.model.NotEligibleReason
import br.ufv.hashlens.testing.SignatureVectors
import br.ufv.hashlens.testing.TestDevices
import br.ufv.hashlens.testing.inMemoryDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigInteger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 002/T041 e 020/T032: vínculo do aparelho antes de cada registro, com o atestador (020 research
 * R11 e data-model §2.2).
 *
 * Keystore, atestador, leitura do contrato, carteira e recibos são falsos; o banco é um Room em
 * memória. Vínculos nunca são revogados: a única transação possível é `registerDevice`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EnsureDeviceBoundUseCaseTest {
    private val db: AppDatabase = inMemoryDatabase()
    private val bindings = db.deviceBindingDao()
    private val keyManager = mockk<DeviceKeyManager>()
    private val attester = mockk<AttesterClient>()
    private val reader = mockk<RegistryReader>()
    private val walletState =
        MutableStateFlow<WalletState>(WalletState.Connected(WALLET_A, ChainConfig.SEPOLIA_CHAIN_ID))
    private val wallet = mockk<WalletSession> { every { state } returns walletState }
    private val receipts = mockk<ReceiptTracker>()
    private val txBuilder = mockk<RegistryTxBuilder>()
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")
    private val clock = MutableClock(NOW)
    private val useCase =
        EnsureDeviceBoundUseCase(keyManager, attester, reader, wallet, receipts, txBuilder, bindings, config, clock)

    /** Desafios com que a chave foi gerada, na ordem. */
    private val challenges = mutableListOf<AttestationChallenge>()

    @After
    fun closeDatabase() = db.close()

    /** A chave atual do alias e, a cada regeneração, a próxima de [regenerated]. */
    private fun keystore(owner: String, current: ByteArray, vararg regenerated: ByteArray) {
        every { keyManager.getOrCreatePublicKey(owner) } returns current
        val queue = ArrayDeque(regenerated.toList())
        every { keyManager.regenerateWithChallenge(owner, any()) } answers {
            challenges += AttestationChallenge.decode(secondArg())!!
            queue.removeFirst()
        }
        every { keyManager.getAttestationChain(owner) } answers { chainOf(challenges.size) }
    }

    private val chains = mutableMapOf<Int, List<ByteArray>>()

    /** Cadeia da tentativa [attempt], sempre os mesmos arrays (a lista compara os elementos por referência). */
    private fun chainOf(attempt: Int) =
        chains.getOrPut(attempt) { listOf(byteArrayOf(attempt.toByte(), 1), byteArrayOf(attempt.toByte(), 2)) }

    private fun authorization(deadline: Instant = NOW.plus(Duration.ofMinutes(15))) =
        DeviceAuthorization(deadline.epochSecond, ByteArray(32) { 7 }, ByteArray(65) { 9 })

    private fun calldata(key: ByteArray, attempt: Int, auth: DeviceAuthorization) =
        RegistryTxBuilder.registerDevice(key, chainOf(attempt), auth.deadline, auth.signature)

    /** O contrato aceita o vínculo da chave [key] na tentativa [attempt]. */
    private fun chainRegisters(key: ByteArray, attempt: Int, auth: DeviceAuthorization, txHash: String, deviceId: Int) {
        coEvery { txBuilder.estimate(any(), calldata(key, attempt, auth)) } returns ENOUGH
        coEvery { wallet.sendTransaction(CONTRACT, calldata(key, attempt, auth)) } returns txHash
        coEvery { receipts.awaitDeviceRegistered(txHash) } returns deviceId
    }

    private fun onChain(owner: String, vararg devices: Pair<Int, ByteArray>) {
        coEvery { reader.getDevicesOf(owner) } returns devices.map { it.first }
        devices.forEach { (id, key) -> coEvery { reader.getDevice(id) } returns TestDevices.device(id, owner, key) }
    }

    // ───────────────────────────── Já vinculado ─────────────────────

    @Test
    fun `chave ja vinculada nao chama o atestador nem regenera`() = runTest {
        keystore(WALLET_A, KEY_A)
        onChain(WALLET_A, 2 to OLD_KEY, 3 to KEY_A)

        val bound = useCase(WALLET_A)

        bound shouldBe BoundDevice(deviceId = 3, newlyBound = false, walletAddress = WALLET_A)
        verify(exactly = 0) { keyManager.regenerateWithChallenge(any(), any()) }
        coVerify(exactly = 0) { attester.authorize(any(), any(), any()) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().run {
            deviceId shouldBe 3
            publicKeyHex shouldBe KEY_A.toHexString()
            status shouldBe RegistrationStatus.CONFIRMED
            failureReason.shouldBeNull()
        }
    }

    @Test
    fun `aparelho ja vinculado segue registrando com o atestador fora do ar`() = runTest {
        // US2 cenário 3: o atestador só participa do vínculo
        keystore(WALLET_A, KEY_A)
        onChain(WALLET_A, 3 to KEY_A)
        coEvery { attester.authorize(any(), any(), any()) } throws AttesterUnavailableException()

        useCase(WALLET_A) shouldBe BoundDevice(deviceId = 3, newlyBound = false, walletAddress = WALLET_A)
        coVerify(exactly = 0) { attester.authorize(any(), any(), any()) }
    }

    // ───────────────────────────── Vínculo novo ─────────────────────

    @Test
    fun `chave nao vinculada regenera com o desafio da carteira, autoriza e envia registerDevice v2`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A)
        onChain(WALLET_A)
        val auth = authorization()
        coEvery { attester.authorize(WALLET_A, KEY_A, chainOf(1)) } returns auth
        chainRegisters(KEY_A, attempt = 1, auth, TX_1, deviceId = 1)
        val progress = mutableListOf<RegistrationProgress>()

        val bound = useCase(WALLET_A) { progress += it }

        bound shouldBe BoundDevice(deviceId = 1, newlyBound = true, walletAddress = WALLET_A)
        challenges.single() shouldBe AttestationChallenge(config.chainId, CONTRACT, WALLET_A, NOW)
        coVerify(exactly = 1) { wallet.sendTransaction(CONTRACT, calldata(KEY_A, 1, auth)) }
        progress shouldBe listOf(
            RegistrationProgress.VerifyingDevice,
            RegistrationProgress.BindingDevice(newKey = false, cost = ENOUGH)
        )
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().run {
            deviceId shouldBe 1
            publicKeyHex shouldBe KEY_A.toHexString()
            txHash shouldBe TX_1
            status shouldBe RegistrationStatus.CONFIRMED
        }
    }

    @Test
    fun `verificacao vem antes do saldo e da carteira`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A)
        onChain(WALLET_A)
        val order = mutableListOf<String>()
        coEvery { attester.authorize(any(), any(), any()) } answers {
            order += "atestador"
            authorization()
        }
        coEvery { txBuilder.estimate(any(), any()) } answers {
            order += "saldo"
            ENOUGH
        }
        coEvery { wallet.sendTransaction(any(), any()) } answers {
            order += "carteira"
            TX_1
        }
        coEvery { receipts.awaitDeviceRegistered(TX_1) } returns 1

        useCase(WALLET_A)

        order shouldBe listOf("atestador", "saldo", "carteira")
    }

    // ───────────────────────────── Recusa e indisponibilidade ───────

    @Test
    fun `recusa do atestador nao abre a carteira e grava o motivo`() = runTest {
        val expected = mapOf(
            NotEligibleReason.MODIFIED to BindingFailure.NOT_ELIGIBLE_MODIFIED,
            NotEligibleReason.OUTDATED to BindingFailure.NOT_ELIGIBLE_OUTDATED,
            NotEligibleReason.NO_SECURE_HARDWARE to BindingFailure.NOT_ELIGIBLE_NO_SECURE_HARDWARE,
            NotEligibleReason.APP_NOT_GENUINE to BindingFailure.NOT_ELIGIBLE_APP,
            NotEligibleReason.REVOKED to BindingFailure.NOT_ELIGIBLE_REVOKED
        )
        for ((reason, failure) in expected) {
            keystore(WALLET_A, OLD_KEY, KEY_A)
            onChain(WALLET_A)
            coEvery { attester.authorize(any(), any(), any()) } throws DeviceNotEligibleException(reason)

            shouldThrow<DeviceNotEligibleException> { useCase(WALLET_A) }.reason shouldBe reason

            bindings.get(WALLET_A.lowercase()).shouldNotBeNull().run {
                status shouldBe RegistrationStatus.FAILED
                failureReason shouldBe failure
            }
        }
        coVerify(exactly = 0) { txBuilder.estimate(any(), any()) }
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
    }

    @Test
    fun `atestador indisponivel nao abre a carteira`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A)
        onChain(WALLET_A)
        coEvery { attester.authorize(any(), any(), any()) } throws AttesterUnavailableException()

        shouldThrow<AttesterUnavailableException> { useCase(WALLET_A) }

        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().failureReason shouldBe BindingFailure.ATTESTER_UNAVAILABLE
    }

    // ───────────────────────────── Nova tentativa automática ────────

    @Test
    fun `desafio ou chave recusados tentam uma vez de novo com chave nova`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A, KEY_B)
        onChain(WALLET_A)
        val auth = authorization()
        coEvery { attester.authorize(WALLET_A, KEY_A, chainOf(1)) } throws
            AttestationRetryException("CHALLENGE_INVALID")
        coEvery { attester.authorize(WALLET_A, KEY_B, chainOf(2)) } returns auth
        chainRegisters(KEY_B, attempt = 2, auth, TX_1, deviceId = 5)

        useCase(WALLET_A) shouldBe BoundDevice(deviceId = 5, newlyBound = true, walletAddress = WALLET_A)

        challenges.size shouldBe 2
        coVerify(exactly = 1) { wallet.sendTransaction(any(), any()) }
    }

    @Test
    fun `desafio recusado duas vezes vira indisponivel`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A, KEY_B)
        onChain(WALLET_A)
        coEvery { attester.authorize(any(), any(), any()) } throws AttestationRetryException("KEY_MISMATCH")

        shouldThrow<AttesterUnavailableException> { useCase(WALLET_A) }

        challenges.size shouldBe 2
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().failureReason shouldBe BindingFailure.ATTESTER_UNAVAILABLE
    }

    @Test
    fun `autorizacao quase vencida antes de abrir a carteira gera outra`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A, KEY_B)
        onChain(WALLET_A)
        val stale = authorization(deadline = NOW.plusSeconds(30))
        val fresh = authorization()
        coEvery { attester.authorize(WALLET_A, KEY_A, chainOf(1)) } returns stale
        coEvery { attester.authorize(WALLET_A, KEY_B, chainOf(2)) } returns fresh
        coEvery { txBuilder.estimate(any(), calldata(KEY_A, 1, stale)) } returns ENOUGH
        chainRegisters(KEY_B, attempt = 2, fresh, TX_1, deviceId = 6)

        useCase(WALLET_A).deviceId shouldBe 6

        coVerify(exactly = 0) { wallet.sendTransaction(any(), calldata(KEY_A, 1, stale)) }
    }

    @Test
    fun `revert depois do prazo da autorizacao tenta de novo, uma vez`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A, KEY_B)
        onChain(WALLET_A)
        val first = authorization()
        val second = authorization(deadline = NOW.plus(Duration.ofMinutes(40)))
        coEvery { attester.authorize(WALLET_A, KEY_A, chainOf(1)) } returns first
        coEvery { attester.authorize(WALLET_A, KEY_B, chainOf(2)) } returns second
        coEvery { txBuilder.estimate(any(), calldata(KEY_A, 1, first)) } returns ENOUGH
        coEvery { wallet.sendTransaction(CONTRACT, calldata(KEY_A, 1, first)) } answers {
            // A pessoa demorou na carteira: a transação chega depois do prazo e reverte (AuthorizationExpired)
            clock.instant = NOW.plus(Duration.ofMinutes(20))
            TX_1
        }
        coEvery { receipts.awaitDeviceRegistered(TX_1) } throws TransactionException.Reverted(TX_1)
        chainRegisters(KEY_B, attempt = 2, second, TX_2, deviceId = 7)

        useCase(WALLET_A).deviceId shouldBe 7

        challenges[1].issuedAt shouldBe NOW.plus(Duration.ofMinutes(20))
    }

    @Test
    fun `revert dentro do prazo nao tenta de novo e grava REVERTED`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A)
        onChain(WALLET_A)
        val auth = authorization()
        coEvery { attester.authorize(any(), any(), any()) } returns auth
        coEvery { txBuilder.estimate(any(), any()) } returns ENOUGH
        coEvery { wallet.sendTransaction(any(), any()) } returns TX_1
        coEvery { receipts.awaitDeviceRegistered(TX_1) } throws TransactionException.Reverted(TX_1)

        shouldThrow<TransactionException.Reverted> { useCase(WALLET_A) }

        challenges.size shouldBe 1
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().failureReason shouldBe BindingFailure.REVERTED
    }

    // ───────────────────────────── Troca de carteira ────────────────

    @Test
    fun `troca de carteira no meio cancela e recomeca para a carteira atual`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A)
        keystore(WALLET_B, OLD_KEY, KEY_B)
        onChain(WALLET_A)
        onChain(WALLET_B)
        val authB = authorization()
        coEvery { attester.authorize(WALLET_A, any(), any()) } answers {
            walletState.value = WalletState.Connected(WALLET_B, ChainConfig.SEPOLIA_CHAIN_ID)
            authorization()
        }
        coEvery { attester.authorize(WALLET_B, KEY_B, any()) } returns authB
        val calldataB = slot<String>()
        coEvery { txBuilder.estimate(any(), any()) } returns ENOUGH
        coEvery { wallet.sendTransaction(CONTRACT, capture(calldataB)) } returns TX_2
        coEvery { receipts.awaitDeviceRegistered(TX_2) } returns 8

        val bound = useCase(WALLET_A)

        bound shouldBe BoundDevice(deviceId = 8, newlyBound = true, walletAddress = WALLET_B)
        coVerify(exactly = 1) { wallet.sendTransaction(any(), any()) }
        calldataB.captured shouldBe RegistryTxBuilder.registerDevice(KEY_B, chainOf(2), authB.deadline, authB.signature)
        challenges.last().wallet shouldBe WALLET_B.lowercase()
        bindings.get(WALLET_B.lowercase()).shouldNotBeNull().deviceId shouldBe 8
    }

    // ───────────────────────────── Saldo e chave perdida ────────────

    @Test
    fun `saldo insuficiente para registerDevice nao abre a carteira`() = runTest {
        keystore(WALLET_A, OLD_KEY, KEY_A)
        onChain(WALLET_A)
        coEvery { attester.authorize(any(), any(), any()) } returns authorization()
        coEvery { txBuilder.estimate(WALLET_A, any()) } returns NOT_ENOUGH

        shouldThrow<InsufficientFundsException> { useCase(WALLET_A) }.cost shouldBe NOT_ENOUGH

        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().run {
            status shouldBe RegistrationStatus.FAILED
            failureReason shouldBe BindingFailure.INSUFFICIENT_FUNDS
        }
    }

    @Test
    fun `chave diferente da do vinculo local avisa que e um novo vinculo`() = runTest {
        bindings.upsert(
            DeviceBindingEntity(
                walletAddress = WALLET_A.lowercase(),
                publicKeyHex = OLD_KEY.toHexString(),
                deviceId = 1,
                status = RegistrationStatus.CONFIRMED
            )
        )
        keystore(WALLET_A, KEY_A, KEY_B)
        onChain(WALLET_A, 1 to OLD_KEY)
        val auth = authorization()
        coEvery { attester.authorize(any(), any(), any()) } returns auth
        chainRegisters(KEY_B, attempt = 1, auth, TX_2, deviceId = 2)
        val progress = mutableListOf<RegistrationProgress>()

        useCase(WALLET_A) { progress += it }

        progress.last() shouldBe RegistrationProgress.BindingDevice(newKey = true, cost = ENOUGH)
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().run {
            publicKeyHex shouldBe KEY_B.toHexString()
            deviceId shouldBe 2
            txHash shouldBe TX_2
            status shouldBe RegistrationStatus.CONFIRMED
        }
    }

    @Test
    fun `segunda carteira no mesmo aparelho tem deviceId proprio`() = runTest {
        keystore(WALLET_A, KEY_A)
        onChain(WALLET_A, 3 to KEY_A)
        useCase(WALLET_A) shouldBe BoundDevice(deviceId = 3, newlyBound = false, walletAddress = WALLET_A)

        walletState.value = WalletState.Connected(WALLET_B, ChainConfig.SEPOLIA_CHAIN_ID)
        keystore(WALLET_B, OLD_KEY, KEY_B)
        onChain(WALLET_B)
        val auth = authorization()
        coEvery { attester.authorize(WALLET_B, KEY_B, any()) } returns auth
        chainRegisters(KEY_B, attempt = 1, auth, TX_1, deviceId = 4)

        useCase(WALLET_B) shouldBe BoundDevice(deviceId = 4, newlyBound = true, walletAddress = WALLET_B)
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().deviceId shouldBe 3
        bindings.get(WALLET_B.lowercase()).shouldNotBeNull().deviceId shouldBe 4
    }

    /** Relógio que o teste pode adiantar. */
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = instant
    }

    private companion object {
        val CONTRACT = SignatureVectors.case("original").payload.contract
        val NOW: Instant = Instant.parse("2026-11-18T12:00:00Z")
        const val WALLET_A = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val WALLET_B = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01"
        const val TX_1 = "0x1111111111111111111111111111111111111111111111111111111111111111"
        const val TX_2 = "0x2222222222222222222222222222222222222222222222222222222222222222"

        // Chaves P-256 não comprimidas (65 bytes, prefixo 0x04): o caso de uso só compara bytes
        val KEY_A: ByteArray = SignatureVectors.publicKeyBytes
        val KEY_B: ByteArray = byteArrayOf(0x04) + ByteArray(64) { (it + 1).toByte() }
        val OLD_KEY: ByteArray = byteArrayOf(0x04) + ByteArray(64) { (0xA0 + it).toByte() }
        val GAS: BigInteger = BigInteger.valueOf(100_000)
        val ENOUGH = TxCost(gasLimit = GAS, gasPrice = BigInteger.TEN, balance = BigInteger.TEN.pow(18))
        val NOT_ENOUGH = TxCost(gasLimit = GAS, gasPrice = BigInteger.TEN, balance = BigInteger.ONE)
    }
}
