package br.ufv.hashlens.ui.navigation

import br.ufv.hashlens.data.local.OnboardingPreferences

/** Destino inicial do app: "Como funciona" no primeiro uso (FR-037), depois o Início. */
fun startRoute(preferences: OnboardingPreferences): Route = if (preferences.hasSeen()) Route.Home else Route.Onboarding
