package br.ufv.hashlens.data.wallet

import android.app.Application
import android.util.Log
import br.ufv.hashlens.config.ChainConfig
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.request.Request
import com.reown.appkit.client.models.request.SentRequestResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject

/** Conta conectada vista pelo app. */
sealed interface WalletState {
    data object Disconnected : WalletState

    /**
     * @param address endereço da carteira como ela informou (checksum preservado).
     * @param chainId rede atual da carteira; nulo se ela não informou.
     */
    data class Connected(val address: String, val chainId: Long?) : WalletState {
        val isSepolia: Boolean get() = chainId == ChainConfig.SEPOLIA_CHAIN_ID
    }
}

/** Respostas da carteira a um pedido de conexão que não viram [WalletState]. */
sealed interface WalletEvent {
    /** A pessoa recusou a conexão na carteira, ou o pedido expirou sem resposta. */
    data object ConnectionRejected : WalletEvent
}

/**
 * Sessão com a carteira externa via Reown AppKit (research R1). O app nunca vê chaves privadas
 * (Constituição III): só pede à carteira que assine e envie transações.
 *
 * A conexão é aberta pela UI com a folha do AppKit; aqui ficam o estado da conta e da rede
 * ([state]), a troca para a Sepolia, o envio de transações e a desconexão. [initialize] roda uma
 * vez na abertura do app.
 */
@Singleton
class WalletSession @Inject constructor(private val config: ChainConfig) {
    private val _state = MutableStateFlow<WalletState>(WalletState.Disconnected)
    val state: StateFlow<WalletState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WalletEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<WalletEvent> = _events.asSharedFlow()

    /** Pedidos aguardando resposta da carteira, pelo id JSON-RPC. */
    private val pending = ConcurrentHashMap<Long, CancellableContinuation<String>>()

    @Volatile
    private var initialized = false

    /** Inicializa o relay e o AppKit. Sem `reown.projectId` configurado, a carteira fica indisponível. */
    fun initialize(application: Application) {
        if (initialized || config.reownProjectId.isBlank()) return
        CoreClient.initialize(
            application = application,
            projectId = config.reownProjectId,
            metaData = METADATA,
            connectionType = ConnectionType.AUTOMATIC,
            onError = { Log.e(TAG, "falha ao iniciar o relay", it.throwable) }
        )
        AppKit.initialize(
            init = Modal.Params.Init(core = CoreClient, coinbaseEnabled = false),
            onSuccess = {
                initialized = true
                AppKit.setChains(listOf(sepolia()))
                refresh()
            },
            onError = { Log.e(TAG, "falha ao iniciar o AppKit", it.throwable) }
        )
        AppKit.setDelegate(delegate)
    }

    /**
     * Pede à carteira que troque para a Sepolia (EIP-3326). A carteira pode recusar
     * ([WalletException.Rejected]) ou não conhecer a rede ([WalletException.Failed] com `4902`).
     */
    suspend fun switchToSepolia() {
        requireConnected()
        val params = JSONArray().put(JSONObject().put("chainId", SEPOLIA_HEX))
        request(METHOD_SWITCH_CHAIN, params)
        updateChain(ChainConfig.SEPOLIA_CHAIN_ID)
    }

    /**
     * Abre a transação na carteira (`eth_sendTransaction`) e espera a pessoa aprovar.
     *
     * @param to endereço do contrato.
     * @param data calldata em hex com `0x`.
     * @return `txHash` com `0x`.
     * @throws WalletException se não houver conta, a rede não for a Sepolia, a pessoa recusar ou a
     *   carteira falhar.
     */
    suspend fun sendTransaction(to: String, data: String): String {
        val account = requireConnected()
        if (!account.isSepolia) throw WalletException.WrongNetwork(account.chainId)
        val transaction = JSONObject()
            .put("from", account.address)
            .put("to", to)
            .put("data", data)
        val txHash = request(METHOD_SEND_TRANSACTION, JSONArray().put(transaction))
        if (!TX_HASH.matches(txHash)) throw WalletException.Failed(null, "txHash inválido: $txHash")
        return txHash.lowercase()
    }

    /** Encerra a sessão; o estado volta a [WalletState.Disconnected] mesmo se o relay falhar. */
    /**
     * Volta do app da carteira para o HashLens (`hashlens-wc://`, redirect do [METADATA]): entrega o
     * link ao AppKit para concluir a conexão ou a resposta do pedido.
     */
    fun handleDeepLink(uri: String) {
        if (!initialized || !uri.startsWith(REDIRECT_SCHEME)) return
        AppKit.handleDeepLink(uri) { Log.w(TAG, "link da carteira não reconhecido", it.throwable) }
    }

    suspend fun disconnect() {
        if (initialized) {
            suspendCancellableCoroutine { continuation ->
                AppKit.disconnect(
                    onSuccess = { continuation.resume(Unit) },
                    onError = {
                        Log.w(TAG, "falha ao desconectar no relay", it)
                        continuation.resume(Unit)
                    }
                )
            }
        }
        _state.value = WalletState.Disconnected
    }

    private fun requireConnected(): WalletState.Connected {
        if (!initialized) throw WalletException.Unavailable()
        return _state.value as? WalletState.Connected ?: throw WalletException.NotConnected()
    }

    private suspend fun request(method: String, params: JSONArray): String =
        suspendCancellableCoroutine { continuation ->
            val request = Request(method = method, params = params.toString(), chainId = SEPOLIA_CAIP2)
            AppKit.request(
                request = request,
                onSuccess = { sent ->
                    when (sent) {
                        is SentRequestResult.WalletConnect -> {
                            pending[sent.requestId] = continuation
                            continuation.invokeOnCancellation { pending.remove(sent.requestId) }
                        }
                        // Coinbase está desativada em initialize
                        else -> continuation.resumeWithException(WalletException.Unavailable())
                    }
                },
                onError = { continuation.resumeWithException(WalletException.Unavailable(it)) }
            )
        }

    private fun refresh() {
        val account = AppKit.getAccount()
        _state.value = if (account == null) {
            WalletState.Disconnected
        } else {
            WalletState.Connected(account.address, account.chain.chainReference.toLongOrNull())
        }
    }

    private fun updateChain(chainId: Long?) {
        _state.value = (_state.value as? WalletState.Connected)?.copy(chainId = chainId) ?: return
    }

    private fun complete(response: Modal.Model.JsonRpcResponse) {
        val continuation = pending.remove(response.id) ?: return
        when (response) {
            is Modal.Model.JsonRpcResponse.JsonRpcResult -> continuation.resume(response.result.toString().trim('"'))
            is Modal.Model.JsonRpcResponse.JsonRpcError -> continuation.resumeWithException(errorFor(response))
        }
    }

    // A interface do AppKit exige todos estes métodos
    @Suppress("TooManyFunctions")
    private val delegate = object : AppKit.ModalDelegate {
        override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) = refresh()

        override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
            _events.tryEmit(WalletEvent.ConnectionRejected)
        }

        override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) = refresh()

        @Deprecated("substituído por onSessionEvent(Modal.Model.Event)")
        override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) = Unit

        override fun onSessionEvent(sessionEvent: Modal.Model.Event) {
            when (sessionEvent.name) {
                EVENT_CHAIN_CHANGED -> updateChain(parseChainId(sessionEvent.data))
                EVENT_ACCOUNTS_CHANGED -> refresh()
            }
        }

        override fun onSessionExtend(session: Modal.Model.Session) = Unit

        override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
            _state.value = WalletState.Disconnected
        }

        override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) = complete(response.result)

        override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {
            _events.tryEmit(WalletEvent.ConnectionRejected)
        }

        override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {
            pending.remove(request.id)?.resumeWithException(WalletException.Expired())
        }

        override fun onConnectionStateChange(state: Modal.Model.ConnectionState) = Unit

        override fun onError(error: Modal.Model.Error) {
            Log.w(TAG, "erro do AppKit", error.throwable)
        }
    }

    internal companion object {
        private const val TAG = "WalletSession"
        private const val EVENT_BUFFER = 4

        /** Esquema que traz a pessoa de volta do app da carteira (intent-filter da MainActivity). */
        const val REDIRECT_SCHEME = "hashlens-wc://"
        private const val NAMESPACE = "eip155"
        private const val SEPOLIA_CAIP2 = "$NAMESPACE:${ChainConfig.SEPOLIA_CHAIN_ID}"
        private const val SEPOLIA_HEX = "0xaa36a7"
        private const val METHOD_SEND_TRANSACTION = "eth_sendTransaction"
        private const val METHOD_SWITCH_CHAIN = "wallet_switchEthereumChain"
        private const val EVENT_CHAIN_CHANGED = "chainChanged"
        private const val EVENT_ACCOUNTS_CHANGED = "accountsChanged"
        private const val USER_REJECTED = 4001
        private const val HEX_RADIX = 16
        private val TX_HASH = Regex("0x[0-9a-fA-F]{64}")

        private val METADATA = Core.Model.AppMetaData(
            name = "HashLens",
            description = "Registro e verificação de imagens na Ethereum Sepolia",
            url = "https://github.com/laixrsales/HashLens",
            icons = emptyList(),
            redirect = "${REDIRECT_SCHEME}request"
        )

        private fun sepolia() = Modal.Model.Chain(
            chainName = "Sepolia",
            chainNamespace = NAMESPACE,
            chainReference = ChainConfig.SEPOLIA_CHAIN_ID.toString(),
            requiredMethods = listOf(METHOD_SEND_TRANSACTION),
            optionalMethods = listOf(METHOD_SWITCH_CHAIN),
            events = listOf(EVENT_CHAIN_CHANGED, EVENT_ACCOUNTS_CHANGED),
            token = Modal.Model.Token(name = "Sepolia Ether", symbol = "ETH", decimal = 18),
            blockExplorerUrl = ChainConfig.SEPOLIA_EXPLORER_URL
        )

        /** `chainChanged` chega em hex (`0xaa36a7`), decimal ou CAIP-2 (`eip155:11155111`), conforme a carteira. */
        fun parseChainId(data: String): Long? {
            val value = data.trim().trim('"').substringAfterLast(':')
            return if (value.startsWith("0x", ignoreCase = true)) {
                value.drop(2).toLongOrNull(HEX_RADIX)
            } else {
                value.toLongOrNull()
            }
        }

        /** Recusa da pessoa (EIP-1193 `4001`; algumas carteiras só dizem "rejected" na mensagem). */
        fun errorFor(error: Modal.Model.JsonRpcResponse.JsonRpcError): WalletException {
            val rejected = error.code == USER_REJECTED ||
                REJECTION_WORDS.any { error.message.contains(it, ignoreCase = true) }
            return if (rejected) WalletException.Rejected() else WalletException.Failed(error.code, error.message)
        }

        private val REJECTION_WORDS = listOf("reject", "denied", "cancel")
    }
}
