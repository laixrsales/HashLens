package br.ufv.hashlens.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * Rota "Como funciona" (ux.md §4.1): destino inicial no primeiro uso e aberta pelo botão do Início.
 *
 * @param onFinishFirstUse leva ao Início no lugar desta tela.
 * @param onClose volta ao Início.
 */
@Composable
fun OnboardingRoute(
    onFinishFirstUse: () -> Unit,
    onClose: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.FinishFirstUse -> onFinishFirstUse()
                OnboardingEffect.Close -> onClose()
            }
        }
    }
    OnboardingScreen(
        onStart = { viewModel.onAction(OnboardingAction.Start) },
        onSkip = { viewModel.onAction(OnboardingAction.Skip) }
    )
}
