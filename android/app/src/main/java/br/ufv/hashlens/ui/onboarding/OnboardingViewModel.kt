package br.ufv.hashlens.ui.onboarding

import androidx.lifecycle.ViewModel
import br.ufv.hashlens.data.local.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface OnboardingAction {
    data object Start : OnboardingAction

    data object Skip : OnboardingAction
}

sealed interface OnboardingEffect {
    /** Primeiro uso concluído: o Início substitui a tela (voltar não a reabre). */
    data object FinishFirstUse : OnboardingEffect

    /** Aberta pelo botão "Como funciona": volta ao Início. */
    data object Close : OnboardingEffect
}

/**
 * "Como funciona" (T105, FR-037, ux.md §4.1). Decide pelo que estava gravado ao abrir: ainda não
 * vista é o primeiro uso, em que "Começar" e "Pular" gravam a visita e levam ao Início; já vista, foi
 * aberta pelo Início, e os dois só voltam.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val preferences: OnboardingPreferences) : ViewModel() {
    private val firstUse = !preferences.hasSeen()
    private var finished = false

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    fun onAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Start, OnboardingAction.Skip -> finish()
        }
    }

    private fun finish() {
        if (!firstUse) {
            _effects.trySend(OnboardingEffect.Close)
            return
        }
        // Dois toques seguidos: o Início entra uma vez só
        if (finished) return
        finished = true
        preferences.markSeen()
        _effects.trySend(OnboardingEffect.FinishFirstUse)
    }
}
