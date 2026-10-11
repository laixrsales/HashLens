package br.ufv.hashlens.ui.verify

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufv.hashlens.core.imaging.ImageDecoder
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.di.DefaultDispatcher
import br.ufv.hashlens.domain.model.VerificationResult
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.domain.usecase.VerifyImageUseCase
import br.ufv.hashlens.ui.capture.toThumbnail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado do fluxo de verificação: Verificar imagem e, quando há resposta, o Resultado.
 *
 * @property result `null` enquanto a tela Verificar imagem está à vista.
 */
data class VerifyFlowUiState(
    val verify: VerifyUiState = VerifyUiState.Idle,
    val result: VerificationResultUiState? = null
)

sealed interface VerifyEffect {
    data object Close : VerifyEffect

    /** Abre o Photo Picker, único ponto de entrada da verificação (FR-019). */
    data object OpenPicker : VerifyEffect

    data class OpenHistory(val recordId: Long) : VerifyEffect

    data class OpenUrl(val url: String) : VerifyEffect
}

/**
 * Verificar imagem → Resultado (T065, ux.md §4.7 e §4.8). Sem carteira e sem custo: só leituras
 * do contrato (FR-019).
 *
 * Com o argumento [RECORD_ID] (vindo de "Ver registro" no Acompanhamento), mostra o resultado
 * daquele registro sem passar pelo Photo Picker.
 */
@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val verifyImage: VerifyImageUseCase,
    private val gallery: GalleryRepository,
    private val decoder: ImageDecoder,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _ui = MutableStateFlow(VerifyFlowUiState())
    val ui: StateFlow<VerifyFlowUiState> = _ui.asStateFlow()

    private val _effects = Channel<VerifyEffect>(Channel.BUFFERED)
    val effects: Flow<VerifyEffect> = _effects.receiveAsFlow()

    private val recordId: Long? = savedStateHandle.get<Long>(RECORD_ID)

    /** Última imagem escolhida, para "Tentar de novo" sem abrir o seletor outra vez. */
    private var picked: PickedImage? = null
    private var running: Job? = null

    init {
        if (recordId != null) runRecord(recordId)
    }

    fun onVerifyAction(action: VerifyAction) {
        when (action) {
            VerifyAction.Back -> send(VerifyEffect.Close)
            VerifyAction.ChooseImage -> send(VerifyEffect.OpenPicker)
        }
    }

    /** Volta do Photo Picker; `null` quando a pessoa fechou sem escolher. */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        running?.cancel()
        _ui.value = VerifyFlowUiState(verify = VerifyUiState.Analyzing(AnalysisStep.Reading))
        running = viewModelScope.launch {
            val bytes = try {
                gallery.read(uri)
            } catch (_: IOException) {
                // Arquivo removido ou sem acesso: nada a verificar
                _ui.value = VerifyFlowUiState(verify = VerifyUiState.UnsupportedFile)
                return@launch
            }
            val image = PickedImage(bytes, thumbnail(bytes))
            picked = image
            verify(image)
        }
    }

    fun onResultAction(action: VerificationResultAction) {
        when (action) {
            VerificationResultAction.Back -> send(VerifyEffect.Close)
            VerificationResultAction.VerifyAnother -> {
                running?.cancel()
                picked = null
                _ui.value = VerifyFlowUiState()
                send(VerifyEffect.OpenPicker)
            }
            VerificationResultAction.Retry -> retry()
            is VerificationResultAction.ViewHistory -> send(VerifyEffect.OpenHistory(action.recordId))
            is VerificationResultAction.OpenUrl -> send(VerifyEffect.OpenUrl(action.url))
        }
    }

    private fun retry() {
        val image = picked
        when {
            image != null -> {
                running?.cancel()
                running = viewModelScope.launch { verify(image) }
            }
            recordId != null -> runRecord(recordId)
        }
    }

    private suspend fun verify(image: PickedImage) {
        _ui.value = VerifyFlowUiState(verify = VerifyUiState.Analyzing(AnalysisStep.Reading, image.thumbnail))
        val result = verifyImage(image.bytes) {
            _ui.update { it.copy(verify = VerifyUiState.Analyzing(AnalysisStep.Querying, image.thumbnail)) }
        }
        show(result, image.thumbnail)
    }

    private fun runRecord(id: Long) {
        running?.cancel()
        _ui.value = VerifyFlowUiState(verify = VerifyUiState.Analyzing(AnalysisStep.Querying))
        running = viewModelScope.launch { show(verifyImage.verifyRecord(id), thumbnail = null) }
    }

    /** O arquivo não suportado fica em Verificar imagem (ux.md §4.7); o resto vai ao Resultado. */
    private fun show(result: VerificationResult, thumbnail: ImageBitmap?) {
        _ui.value = if (result == VerificationResult.Error(Reason.UNSUPPORTED_FILE)) {
            VerifyFlowUiState(verify = VerifyUiState.UnsupportedFile)
        } else {
            VerifyFlowUiState(result = VerificationResultUiState(result, thumbnail))
        }
    }

    /** Miniatura já na orientação do EXIF; `null` se o Android não decodifica (o resultado decide o formato). */
    private suspend fun thumbnail(bytes: ByteArray): ImageBitmap? = withContext(defaultDispatcher) {
        try {
            decoder.decode(bytes).toThumbnail(rotationDegrees = 0)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun send(effect: VerifyEffect) {
        _effects.trySend(effect)
    }

    private class PickedImage(val bytes: ByteArray, val thumbnail: ImageBitmap?)

    companion object {
        /** Argumento de navegação do Resultado aberto por id de registro. */
        const val RECORD_ID = "recordId"
    }
}
