package br.ufv.hashlens.ui.library

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufv.hashlens.R
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.ImageDecoder
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.data.wallet.WalletState
import br.ufv.hashlens.di.ApplicationScope
import br.ufv.hashlens.di.DefaultDispatcher
import br.ufv.hashlens.domain.usecase.CaptureResult
import br.ufv.hashlens.domain.usecase.RetryRegistrationUseCase
import br.ufv.hashlens.ui.common.AddressFormat
import br.ufv.hashlens.work.PendingTxScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LibraryEffect {
    data object Close : LibraryEffect

    /** Estado vazio: "Registrar foto", que passa pela carteira como no Início. */
    data object RegisterPhoto : LibraryEffect

    data class OpenHistory(val recordId: Long) : LibraryEffect

    /** Editor com a foto do acervo como pai. */
    data class Edit(val localId: Long) : LibraryEffect

    /** Aviso curto (snackbar); [arg] preenche o `%1$s` do texto. */
    data class Notice(@StringRes val text: Int, val arg: String? = null) : LibraryEffect
}

/**
 * Meu acervo (T089, ux.md §4.10; US6, FR-029): acompanha o acervo local e monta os grupos pela
 * original ([toLibrary]), com o filtro escolhido. "Tentar de novo" passa pelo
 * [RetryRegistrationUseCase] (checagem do hash antes de abrir a carteira, R23) e roda no
 * [applicationScope], como na captura: sair da tela não cancela a espera pela carteira.
 *
 * Ao aparecer no acervo, cada foto é relida da galeria uma vez (T090): apagada ou alterada (SHA-256
 * diferente do registrado) vira `fileAvailable = false`, de volta vira `true`. Da mesma leitura sai a
 * miniatura.
 */
@Suppress("LongParameterList")
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val images: LocalImageDao,
    private val retryRegistration: RetryRegistrationUseCase,
    private val session: WalletSession,
    private val scheduler: PendingTxScheduler,
    private val gallery: GalleryRepository,
    private val decoder: ImageDecoder,
    private val sha256Hasher: Sha256Hasher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter.All)
    private val selected = MutableStateFlow<Long?>(null)
    private val retrying = MutableStateFlow<Set<Long>>(emptySet())
    private val thumbnails = MutableStateFlow<Map<Long, ImageBitmap>>(emptyMap())

    /** Fotos já conferidas na galeria nesta abertura do acervo. */
    private val inspected = mutableSetOf<Long>()

    val ui: StateFlow<LibraryUiState> = combine(
        images.observeAll(),
        filter,
        selected,
        retrying,
        thumbnails
    ) { stored, filter, selected, retrying, thumbnails ->
        stored.toLibrary(filter, retrying, thumbnails).copy(selected = selected)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    private val _effects = Channel<LibraryEffect>(Channel.BUFFERED)
    val effects: Flow<LibraryEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            images.observeAll().collect { stored ->
                stored.filter { inspected.add(it.localId) }.forEach { inspect(it) }
            }
        }
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            LibraryAction.Back -> send(LibraryEffect.Close)
            LibraryAction.RegisterPhoto -> send(LibraryEffect.RegisterPhoto)
            is LibraryAction.SetFilter -> filter.value = action.filter
            is LibraryAction.Select -> selected.value = action.localId
            LibraryAction.DismissSelection -> selected.value = null
            is LibraryAction.Retry -> {
                selected.value = null
                retry(action.localId)
            }
            is LibraryAction.OpenHistory -> {
                selected.value = null
                send(LibraryEffect.OpenHistory(action.recordId))
            }
            is LibraryAction.Edit -> {
                selected.value = null
                send(LibraryEffect.Edit(action.localId))
            }
        }
    }

    private fun retry(localId: Long) {
        if (localId in retrying.value) return
        val wallet = (session.state.value as? WalletState.Connected)?.takeIf { it.isSepolia }?.address
            ?: return send(LibraryEffect.Notice(R.string.notice_wallet_not_ready))
        retrying.update { it + localId }
        applicationScope.launch {
            val result = try {
                retryRegistration(localId, wallet)
            } catch (_: IllegalArgumentException) {
                // Já saiu de FAILED por outro caminho (ex.: o worker confirmou); o acervo mostra o novo selo
                null
            } finally {
                retrying.update { it - localId }
            }
            result?.let(::onRetryResult)
        }
    }

    private fun onRetryResult(result: CaptureResult) {
        when (result) {
            is CaptureResult.Submitted -> scheduler.trackPending()
            is CaptureResult.WrongWallet ->
                send(LibraryEffect.Notice(R.string.notice_wrong_wallet_retry, AddressFormat.short(result.expected)))
            is CaptureResult.InsufficientFunds -> send(LibraryEffect.Notice(R.string.notice_insufficient_funds_retry))
            is CaptureResult.Failed ->
                if (result.reason == FailureReason.NETWORK) {
                    send(LibraryEffect.Notice(R.string.notice_network_retry))
                }
            // O selo muda sozinho pelo acervo; a recusa na carteira é decisão da pessoa
            is CaptureResult.Confirmed, is CaptureResult.AlreadyRegistered -> Unit
        }
    }

    /** Relê o arquivo: corrige `fileAvailable` se mudou e guarda a miniatura de quem está intacta. */
    private suspend fun inspect(image: LocalImageEntity) {
        val bytes = try {
            gallery.read(Uri.parse(image.contentUri))
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            // Sem permissão para o arquivo (ex.: outro app passou a ser o dono)
            null
        }
        val intact = bytes != null &&
            withContext(defaultDispatcher) { sha256Hasher.hash(bytes.inputStream()).hex == image.sha256Hex }
        if (intact != image.fileAvailable) images.setFileAvailable(image.localId, intact)
        if (bytes == null || !intact) return
        val thumbnail = withContext(defaultDispatcher) {
            try {
                decoder.decodeThumbnail(bytes, THUMBNAIL_SIDE).asImageBitmap()
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: return
        thumbnails.update { it + (image.localId to thumbnail) }
    }

    private fun send(effect: LibraryEffect) {
        _effects.trySend(effect)
    }

    private companion object {
        /** Lado maior da miniatura: uma célula da grade (~190 dp) em telas xxhdpi. */
        const val THUMBNAIL_SIDE = 512
    }
}
