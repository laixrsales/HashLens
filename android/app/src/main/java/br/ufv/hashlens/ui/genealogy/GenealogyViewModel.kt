package br.ufv.hashlens.ui.genealogy

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.core.imaging.ImageDecoder
import br.ufv.hashlens.data.gallery.GalleryRepository
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.di.DefaultDispatcher
import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.domain.model.RecordView
import br.ufv.hashlens.domain.usecase.GenealogyResult
import br.ufv.hashlens.domain.usecase.TraceGenealogyUseCase
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

sealed interface GenealogyEffect {
    data object Close : GenealogyEffect

    /** Abre o Histórico de outra versão da árvore. */
    data class OpenRecord(val recordId: Long) : GenealogyEffect

    /** Abre a transação no explorador de blocos. */
    data class OpenUrl(val url: String) : GenealogyEffect
}

/**
 * Histórico da imagem (T083, ux.md §4.9; US5): a genealogia do registro [RECORD_ID], com a linha do
 * tempo da original até ele e as outras versões recolhidas. Pública como a verificação: só leituras
 * do contrato, sem carteira e sem custo.
 *
 * As miniaturas chegam depois da genealogia, uma a uma: só das versões da linha do tempo que estão
 * neste aparelho e cujo arquivo ainda é o registrado (o SHA-256 confere).
 */
@HiltViewModel
class GenealogyViewModel @Inject constructor(
    private val traceGenealogy: TraceGenealogyUseCase,
    private val images: LocalImageDao,
    private val gallery: GalleryRepository,
    private val decoder: ImageDecoder,
    private val sha256Hasher: Sha256Hasher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _ui = MutableStateFlow<GenealogyUiState>(GenealogyUiState.Loading)
    val ui: StateFlow<GenealogyUiState> = _ui.asStateFlow()

    private val _effects = Channel<GenealogyEffect>(Channel.BUFFERED)
    val effects: Flow<GenealogyEffect> = _effects.receiveAsFlow()

    private val recordId: Long = checkNotNull(savedStateHandle.get<Long>(RECORD_ID)) { "Histórico sem $RECORD_ID" }
    private var running: Job? = null

    init {
        load()
    }

    fun onAction(action: GenealogyAction) {
        when (action) {
            GenealogyAction.Back -> send(GenealogyEffect.Close)
            GenealogyAction.Retry -> load()
            is GenealogyAction.ToggleBranches -> _ui.update { state ->
                if (state !is GenealogyUiState.Loaded) return@update state
                val expanded = state.expanded
                state.copy(
                    expanded = if (action.recordId in
                        expanded
                    ) {
                        expanded - action.recordId
                    } else {
                        expanded + action.recordId
                    }
                )
            }
            is GenealogyAction.OpenRecord -> send(GenealogyEffect.OpenRecord(action.recordId))
            is GenealogyAction.OpenTransaction -> send(GenealogyEffect.OpenUrl(action.url))
        }
    }

    private fun load() {
        running?.cancel()
        _ui.value = GenealogyUiState.Loading
        running = viewModelScope.launch {
            when (val result = traceGenealogy(recordId)) {
                is GenealogyResult.Error -> _ui.value = GenealogyUiState.Error(result.reason)
                is GenealogyResult.Traced -> {
                    _ui.value = GenealogyUiState.Loaded(result.genealogy)
                    loadThumbnails(result.genealogy)
                }
            }
        }
    }

    private suspend fun loadThumbnails(genealogy: Genealogy) {
        val records = genealogy.timeline().associate { it.node.record.id to it.node.record }
        val local = images.getConfirmedByRecordIds(records.keys.toList())
            .filter { it.fileAvailable }
            .mapNotNull { image -> image.recordId?.let(records::get)?.let { image to it } }
        for ((image, record) in local) {
            thumbnail(image, record)?.let { thumbnail -> showThumbnail(record.id, thumbnail) }
        }
    }

    private fun showThumbnail(recordId: Long, thumbnail: ImageBitmap) = _ui.update { state ->
        if (state is GenealogyUiState.Loaded) {
            state.copy(
                thumbnails = state.thumbnails + (recordId to thumbnail)
            )
        } else {
            state
        }
    }

    /** Miniatura do arquivo, se ainda for o registrado; `null` se saiu da galeria, mudou ou não decodifica. */
    private suspend fun thumbnail(image: LocalImageEntity, record: RecordView): ImageBitmap? = try {
        val bytes = gallery.read(Uri.parse(image.contentUri))
        withContext(defaultDispatcher) {
            bytes.takeIf { sha256Hasher.hash(it.inputStream()).hex.equals(record.sha256, ignoreCase = true) }
                ?.let { decoder.decode(it).toThumbnail(rotationDegrees = 0) }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun send(effect: GenealogyEffect) {
        _effects.trySend(effect)
    }

    companion object {
        /** Argumento de navegação: o registro consultado. */
        const val RECORD_ID = "recordId"
    }
}
