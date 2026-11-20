package br.ufv.hashlens.domain.usecase

import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.data.attester.AttesterClient
import br.ufv.hashlens.data.chain.InsufficientFundsException
import br.ufv.hashlens.data.chain.ReceiptTracker
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.chain.TransactionException
import br.ufv.hashlens.data.local.BindingFailure
import br.ufv.hashlens.data.local.DeviceBindingDao
import br.ufv.hashlens.data.local.DeviceBindingEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.data.wallet.WalletException
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.di.IoDispatcher
import br.ufv.hashlens.domain.model.AttestationRetryException
import br.ufv.hashlens.domain.model.AttesterUnavailableException
import br.ufv.hashlens.domain.model.DeviceAuthorization
import br.ufv.hashlens.domain.model.DeviceNotEligibleException
import br.ufv.hashlens.domain.model.NotEligibleReason
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aparelho vinculado à carteira, pronto para assinar registros.
 *
 * @param newlyBound `true` se este uso precisou de `registerDevice` (a UI mostra "Vinculando
 *   este aparelho…").
 * @param walletAddress carteira do vínculo: a conectada no fim, que pode ser outra se a pessoa
 *   trocou de conta no meio (o registro deve usar esta).
 */
data class BoundDevice(val deviceId: Int, val newlyBound: Boolean, val walletAddress: String)

/**
 * Garante que a chave deste aparelho para a carteira está vinculada on-chain antes de cada registro
 * (002 research R25; 020 research R11 e data-model §2.2).
 *
 * 1. Procura a chave atual do alias em `getDevicesOf(carteira)`; se já está vinculada, termina.
 * 2. Senão, gera a chave de novo com o desafio da atestação (só pode ser definido na geração),
 *    envia a cadeia ao atestador e recebe a autorização. Recusa e indisponibilidade terminam aqui,
 *    sem abrir a carteira (FR-010, FR-011).
 * 3. Confere o saldo (FR-003), envia `registerDevice` v2 pela carteira e espera `DeviceRegistered`.
 *
 * Desafio ou chave recusados, autorização vencida antes do envio ou revert depois do prazo levam a
 * uma única nova tentativa, com chave e desafio novos. Se a carteira conectada mudar no meio, o
 * vínculo recomeça para a carteira atual. Vínculos nunca são revogados: com chave nova, o aparelho
 * antigo segue on-chain e os registros dele continuam válidos.
 */
class EnsureDeviceBoundUseCase @Inject constructor(
    private val keyManager: DeviceKeyManager,
    private val attester: AttesterClient,
    private val reader: RegistryReader,
    private val wallet: WalletSession,
    private val receipts: ReceiptTracker,
    private val txBuilder: RegistryTxBuilder,
    private val bindings: DeviceBindingDao,
    private val config: ChainConfig,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * @param onProgress avisado ao verificar a integridade e antes de abrir a carteira.
     * @throws DeviceNotEligibleException se o atestador recusar o aparelho.
     * @throws AttesterUnavailableException se o atestador não responder ou recusar o desafio duas vezes.
     * @throws InsufficientFundsException se o saldo não cobrir `registerDevice`; a carteira não é aberta.
     * @throws WalletException se a pessoa recusar `registerDevice` ou a carteira falhar.
     * @throws TransactionException se `registerDevice` reverter ou não confirmar no prazo.
     * @throws br.ufv.hashlens.data.chain.RegistryException em falha de leitura do contrato.
     */
    suspend operator fun invoke(walletAddress: String, onProgress: (RegistrationProgress) -> Unit = {}): BoundDevice {
        var address = walletAddress
        repeat(MAX_WALLET_RESTARTS) {
            when (val outcome = bind(address, onProgress)) {
                is Outcome.Bound -> return outcome.device
                is Outcome.WalletChanged -> address = outcome.current
            }
        }
        return when (val outcome = bind(address, onProgress)) {
            is Outcome.Bound -> outcome.device
            is Outcome.WalletChanged -> throw WalletException.Failed(null, "a carteira mudou durante o vínculo")
        }
    }

    private sealed interface Outcome {
        data class Bound(val device: BoundDevice) : Outcome

        data class WalletChanged(val current: String) : Outcome
    }

    private suspend fun bind(walletAddress: String, onProgress: (RegistrationProgress) -> Unit): Outcome {
        val currentKey = withContext(ioDispatcher) { keyManager.getOrCreatePublicKey(walletAddress) }
        val previous = bindings.get(walletAddress.lowercase())
        findOnChain(walletAddress, currentKey)?.let { deviceId ->
            bindings.upsert(
                binding(walletAddress, currentKey).copy(deviceId = deviceId, status = RegistrationStatus.CONFIRMED)
            )
            return Outcome.Bound(BoundDevice(deviceId, newlyBound = false, walletAddress))
        }
        // Já havia vínculo local com outra chave: a do Keystore se perdeu (002 R25)
        val newKey = previous != null && previous.publicKeyHex != currentKey.toHexString()

        var attempt = 1
        while (true) {
            val result = attempt(walletAddress, newKey, isLastAttempt = attempt == MAX_ATTEMPTS, onProgress)
            if (result != null) return result
            attempt++
        }
    }

    /** Chave atestada e autorização de uma tentativa, prontas para o envio. */
    private class Attested(
        val binding: DeviceBindingEntity,
        val publicKey: ByteArray,
        val chain: List<ByteArray>,
        val authorization: DeviceAuthorization
    )

    /** Uma tentativa completa; `null` pede uma nova tentativa (só antes da última). */
    private suspend fun attempt(
        walletAddress: String,
        newKey: Boolean,
        isLastAttempt: Boolean,
        onProgress: (RegistrationProgress) -> Unit
    ): Outcome? {
        val attested = attest(walletAddress, isLastAttempt, onProgress)
        val changed = changedWallet(walletAddress)
        return when {
            attested == null -> null
            changed != null -> Outcome.WalletChanged(changed)
            else -> send(walletAddress, attested, newKey, isLastAttempt, onProgress)
                ?.let { Outcome.Bound(BoundDevice(it, newlyBound = true, walletAddress)) }
        }
    }

    /** Gera a chave com o desafio e pede a autorização; `null` pede nova tentativa. */
    private suspend fun attest(
        walletAddress: String,
        isLastAttempt: Boolean,
        onProgress: (RegistrationProgress) -> Unit
    ): Attested? {
        onProgress(RegistrationProgress.VerifyingDevice)
        val challenge = AttestationChallenge(config.chainId, config.registryAddress, walletAddress, now())
        val (publicKey, chain) = withContext(ioDispatcher) {
            keyManager.regenerateWithChallenge(walletAddress, challenge.encode()) to
                keyManager.getAttestationChain(walletAddress)
        }
        val binding = binding(walletAddress, publicKey)
        bindings.upsert(binding)
        return authorize(walletAddress, binding, publicKey, chain, isLastAttempt)
            ?.let { Attested(binding, publicKey, chain, it) }
    }

    /** Saldo, carteira e recibo; `null` pede nova tentativa (autorização vencida). */
    private suspend fun send(
        walletAddress: String,
        attested: Attested,
        newKey: Boolean,
        isLastAttempt: Boolean,
        onProgress: (RegistrationProgress) -> Unit
    ): Int? {
        val authorization = attested.authorization
        val calldata = RegistryTxBuilder.registerDevice(
            attested.publicKey,
            attested.chain,
            authorization.deadline,
            authorization.signature
        )
        val cost = txBuilder.estimate(walletAddress, calldata)
        if (!cost.hasEnoughBalance) {
            fail(attested.binding, BindingFailure.INSUFFICIENT_FUNDS)
            throw InsufficientFundsException(cost)
        }
        if (isExpiring(authorization, now()) && !isLastAttempt) return null
        onProgress(RegistrationProgress.BindingDevice(newKey, cost))
        return register(attested.binding, calldata, authorization, isLastAttempt)
    }

    /** Autorização do atestador; `null` pede nova tentativa. Recusas ficam gravadas no vínculo. */
    private suspend fun authorize(
        walletAddress: String,
        binding: DeviceBindingEntity,
        publicKey: ByteArray,
        chain: List<ByteArray>,
        isLastAttempt: Boolean
    ): DeviceAuthorization? = try {
        attester.authorize(walletAddress, publicKey, chain)
    } catch (e: DeviceNotEligibleException) {
        fail(binding, e.reason.toBindingFailure())
        throw e
    } catch (e: AttesterUnavailableException) {
        fail(binding, BindingFailure.ATTESTER_UNAVAILABLE)
        throw e
    } catch (e: AttestationRetryException) {
        if (!isLastAttempt) {
            null
        } else {
            fail(binding, BindingFailure.ATTESTER_UNAVAILABLE)
            throw AttesterUnavailableException(e)
        }
    }

    /**
     * Envia `registerDevice` pela carteira e espera o `deviceId` do evento, gravando cada passo.
     * `null` pede nova tentativa: a transação reverteu depois do prazo da autorização.
     */
    private suspend fun register(
        binding: DeviceBindingEntity,
        calldata: String,
        authorization: DeviceAuthorization,
        isLastAttempt: Boolean
    ): Int? {
        bindings.upsert(binding.copy(status = RegistrationStatus.AWAITING_WALLET))
        var txHash: String? = null
        try {
            txHash = wallet.sendTransaction(config.registryAddress, calldata)
            bindings.upsert(binding.copy(txHash = txHash, status = RegistrationStatus.PENDING))
            val deviceId = receipts.awaitDeviceRegistered(txHash)
            bindings.upsert(binding.copy(deviceId = deviceId, txHash = txHash, status = RegistrationStatus.CONFIRMED))
            return deviceId
        } catch (e: WalletException) {
            bindings.upsert(binding.copy(txHash = txHash, status = RegistrationStatus.FAILED))
            throw e
        } catch (e: TransactionException) {
            // AuthorizationExpired: o recibo não traz o motivo, mas o prazo já passou
            val expired =
                e is TransactionException.Reverted && !now().isBefore(Instant.ofEpochSecond(authorization.deadline))
            if (expired && !isLastAttempt) return null
            val failure = if (e is TransactionException.TimedOut) BindingFailure.TIMEOUT else BindingFailure.REVERTED
            fail(binding.copy(txHash = txHash), failure)
            throw e
        }
    }

    private suspend fun fail(binding: DeviceBindingEntity, reason: BindingFailure) {
        bindings.upsert(binding.copy(status = RegistrationStatus.FAILED, failureReason = reason))
    }

    /** Carteira conectada agora, se for outra que não [walletAddress]. */
    private fun changedWallet(walletAddress: String): String? =
        (wallet.state.value as? WalletState.Connected)?.address?.takeUnless {
            it.equals(walletAddress, ignoreCase = true)
        }

    private fun now(): Instant = Instant.ofEpochSecond(clock.instant().epochSecond)

    /** `deviceId` on-chain da carteira com esta chave pública, ou `null`. */
    private suspend fun findOnChain(walletAddress: String, publicKey: ByteArray): Int? =
        // Os mais recentes primeiro: a chave atual costuma ser a do último vínculo
        reader.getDevicesOf(walletAddress).asReversed().firstOrNull { id ->
            reader.getDevice(id)?.publicKey?.contentEquals(publicKey) == true
        }

    private companion object {
        /** A primeira tentativa e uma automática (020 research R11, passo 5). */
        const val MAX_ATTEMPTS = 2

        /** Recomeços por troca de carteira antes de desistir. */
        const val MAX_WALLET_RESTARTS = 2
        val EXPIRY_MARGIN: Duration = Duration.ofMinutes(1)

        /** Falta menos de [EXPIRY_MARGIN] para o prazo: não dá tempo de aprovar na carteira e minerar. */
        fun isExpiring(authorization: DeviceAuthorization, now: Instant): Boolean =
            !now.plus(EXPIRY_MARGIN).isBefore(Instant.ofEpochSecond(authorization.deadline))

        fun binding(walletAddress: String, publicKey: ByteArray) = DeviceBindingEntity(
            walletAddress = walletAddress.lowercase(),
            publicKeyHex = publicKey.toHexString(),
            status = RegistrationStatus.LOCAL_ONLY
        )

        fun NotEligibleReason.toBindingFailure(): BindingFailure = when (this) {
            NotEligibleReason.MODIFIED -> BindingFailure.NOT_ELIGIBLE_MODIFIED
            NotEligibleReason.OUTDATED -> BindingFailure.NOT_ELIGIBLE_OUTDATED
            NotEligibleReason.NO_SECURE_HARDWARE -> BindingFailure.NOT_ELIGIBLE_NO_SECURE_HARDWARE
            NotEligibleReason.APP_NOT_GENUINE -> BindingFailure.NOT_ELIGIBLE_APP
            NotEligibleReason.REVOKED -> BindingFailure.NOT_ELIGIBLE_REVOKED
        }
    }
}
