package br.ufv.hashlens.ui.onboarding

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T038: "Como funciona" (estado único, ux.md §4.1). */
class OnboardingScreenshotTest : ScreenshotTest() {
    @Test
    fun single() = screenSnapshot("Onboarding") { OnboardingScreen(onStart = {}, onSkip = {}) }
}
