package br.ufv.hashlens.domain.usecase

import android.app.Application
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.data.chain.ReceiptTracker
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.local.AppDatabase
import br.ufv.hashlens.data.local.DeviceBindingEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.domain.model.Device
import br.ufv.hashlens.testing.SignatureVectors
import br.ufv.hashlens.testing.inMemoryDatabase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T041: vínculo do aparelho antes de cada registro (research R4 e R25, plan "Vínculo do aparelho").
 *
 * Keystore, leitura do contrato, carteira e recibos são falsos; o banco é um Room em memória.
 * Vínculos nunca são revogados: a única transação possível é `registerDevice`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EnsureDeviceBoundUseCaseTest {
    private val db: AppDatabase = inMemoryDatabase()
    private val bindings = db.deviceBindingDao()
    private val keyManager = mockk<DeviceKeyManager>()
    private val reader = mockk<RegistryReader>()
    private val wallet = mockk<WalletSession>()
    private val receipts = mockk<ReceiptTracker>()
    private val config = ChainConfig(rpcUrl = "", registryAddress = CONTRACT, reownProjectId = "")
    private val useCase = EnsureDeviceBoundUseCase(keyManager, reader, wallet, receipts, bindings, config)

    @After
    fun closeDatabase() = db.close()

    /** A carteira envia a transação e o recibo traz o `deviceId` do evento `DeviceRegistered`. */
    private fun chainRegisters(publicKey: ByteArray, txHash: String, newDeviceId: Int) {
        coEvery { wallet.sendTransaction(CONTRACT, RegistryTxBuilder.registerDevice(publicKey)) } returns txHash
        coEvery { receipts.awaitDeviceRegistered(txHash) } returns newDeviceId
    }

    private fun onChain(owner: String, vararg devices: Pair<Int, ByteArray>) {
        coEvery { reader.getDevicesOf(owner) } returns devices.map { it.first }
        devices.forEach { (id, key) -> coEvery { reader.getDevice(id) } returns Device(id, owner, key) }
    }

    @Test
    fun `chave já vinculada on-chain não gera transação`() = runTest {
        every { keyManager.getOrCreatePublicKey(WALLET_A) } returns KEY_A
        onChain(WALLET_A, 2 to OLD_KEY, 3 to KEY_A)

        val bound = useCase(WALLET_A)

        bound shouldBe BoundDevice(deviceId = 3, newlyBound = false)
        coVerify(exactly = 0) { wallet.sendTransaction(any(), any()) }
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().run {
            deviceId shouldBe 3
            publicKeyHex shouldBe KEY_A.toHexString()
            status shouldBe RegistrationStatus.CONFIRMED
        }
    }

    @Test
    fun `alias ausente gera a chave e chama registerDevice`() = runTest {
        // Sem alias, getOrCreatePublicKey gera a chave no Keystore
        every { keyManager.getOrCreatePublicKey(WALLET_A) } returns KEY_A
        onChain(WALLET_A)
        chainRegisters(KEY_A, TX_1, newDeviceId = 1)

        val bound = useCase(WALLET_A)

        bound shouldBe BoundDevice(deviceId = 1, newlyBound = true)
        verify { keyManager.getOrCreatePublicKey(WALLET_A) }
        coVerify(exactly = 1) { wallet.sendTransaction(CONTRACT, RegistryTxBuilder.registerDevice(KEY_A)) }
        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().run {
            deviceId shouldBe 1
            txHash shouldBe TX_1
            status shouldBe RegistrationStatus.CONFIRMED
        }
    }

    @Test
    fun `chave nova com aparelho antigo on-chain ganha novo deviceId e não mexe no antigo`() = runTest {
        // Chave perdida (dados do app apagados): o Keystore gera outra, e a antiga segue on-chain no aparelho 1
        every { keyManager.getOrCreatePublicKey(WALLET_A) } returns KEY_A
        onChain(WALLET_A, 1 to OLD_KEY)
        chainRegisters(KEY_A, TX_2, newDeviceId = 2)

        val bound = useCase(WALLET_A)

        bound shouldBe BoundDevice(deviceId = 2, newlyBound = true)
        // Uma única transação, e é o registro do aparelho novo: nada revoga o aparelho 1
        coVerify(exactly = 1) { wallet.sendTransaction(any(), any()) }
        coVerify(exactly = 1) { wallet.sendTransaction(CONTRACT, RegistryTxBuilder.registerDevice(KEY_A)) }
    }

    @Test
    fun `segunda carteira no mesmo aparelho tem deviceId próprio`() = runTest {
        every { keyManager.getOrCreatePublicKey(WALLET_A) } returns KEY_A
        every { keyManager.getOrCreatePublicKey(WALLET_B) } returns KEY_B
        onChain(WALLET_A, 3 to KEY_A)
        onChain(WALLET_B)
        chainRegisters(KEY_B, TX_1, newDeviceId = 4)

        useCase(WALLET_A) shouldBe BoundDevice(deviceId = 3, newlyBound = false)
        useCase(WALLET_B) shouldBe BoundDevice(deviceId = 4, newlyBound = true)

        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().deviceId shouldBe 3
        bindings.get(WALLET_B.lowercase()).shouldNotBeNull().run {
            deviceId shouldBe 4
            publicKeyHex shouldBe KEY_B.toHexString()
        }
    }

    @Test
    fun `vínculo local é substituído quando a chave muda`() = runTest {
        bindings.upsert(
            DeviceBindingEntity(
                walletAddress = WALLET_A.lowercase(),
                publicKeyHex = OLD_KEY.toHexString(),
                deviceId = 1,
                txHash = TX_1,
                status = RegistrationStatus.CONFIRMED
            )
        )
        every { keyManager.getOrCreatePublicKey(WALLET_A) } returns KEY_A
        onChain(WALLET_A, 1 to OLD_KEY)
        chainRegisters(KEY_A, TX_2, newDeviceId = 2)

        useCase(WALLET_A)

        bindings.get(WALLET_A.lowercase()).shouldNotBeNull().run {
            publicKeyHex shouldBe KEY_A.toHexString()
            deviceId shouldBe 2
            txHash shouldBe TX_2
            status shouldBe RegistrationStatus.CONFIRMED
        }
    }

    private companion object {
        val CONTRACT = SignatureVectors.case("original").payload.contract
        const val WALLET_A = "0x1234567890AbcdEF1234567890aBcdef12345678"
        const val WALLET_B = "0xAbCdEf0123456789aBcDeF0123456789AbCdEf01"
        const val TX_1 = "0x1111111111111111111111111111111111111111111111111111111111111111"
        const val TX_2 = "0x2222222222222222222222222222222222222222222222222222222222222222"

        // Chaves P-256 não comprimidas (65 bytes, prefixo 0x04): o caso de uso só compara bytes
        val KEY_A: ByteArray = SignatureVectors.publicKeyBytes
        val KEY_B: ByteArray = byteArrayOf(0x04) + ByteArray(64) { (it + 1).toByte() }
        val OLD_KEY: ByteArray = byteArrayOf(0x04) + ByteArray(64) { (0xA0 + it).toByte() }
    }
}
