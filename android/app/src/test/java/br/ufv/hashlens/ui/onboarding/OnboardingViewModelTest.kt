package br.ufv.hashlens.ui.onboarding

import br.ufv.hashlens.data.local.OnboardingPreferences
import br.ufv.hashlens.ui.navigation.Route
import br.ufv.hashlens.ui.navigation.startRoute
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T104: "Como funciona" no primeiro uso (FR-037, ux.md §4.1). Na primeira abertura o app começa por
 * ela; "Começar" e "Pular" gravam que foi vista e levam ao Início, que passa a ser o destino inicial.
 * Aberta depois pelo botão "Como funciona" do Início, os dois só voltam, sem regravar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    /** Preferências em memória, contando as gravações. */
    private class FakePreferences(var seen: Boolean = false) : OnboardingPreferences {
        var writes = 0

        override fun hasSeen(): Boolean = seen

        override fun markSeen() {
            seen = true
            writes++
        }
    }

    private val preferences = FakePreferences()

    @BeforeEach
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.effects(viewModel: OnboardingViewModel): List<OnboardingEffect> {
        val collected = mutableListOf<OnboardingEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { collected += it } }
        return collected
    }

    @Test
    fun `no primeiro uso o app começa pelo Como funciona, e depois pelo Início`() {
        startRoute(preferences) shouldBe Route.Onboarding

        preferences.markSeen()

        startRoute(preferences) shouldBe Route.Home
    }

    @Test
    fun `no primeiro uso, Começar grava que a tela foi vista e leva ao Início`() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(preferences)
        val effects = effects(viewModel)

        viewModel.onAction(OnboardingAction.Start)

        preferences.seen shouldBe true
        effects shouldContainExactly listOf(OnboardingEffect.FinishFirstUse)
        startRoute(preferences) shouldBe Route.Home
    }

    @Test
    fun `no primeiro uso, Pular também grava e leva ao Início`() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(preferences)
        val effects = effects(viewModel)

        viewModel.onAction(OnboardingAction.Skip)

        preferences.seen shouldBe true
        effects shouldContainExactly listOf(OnboardingEffect.FinishFirstUse)
    }

    @Test
    fun `aberta pelo botão Como funciona, Começar e Pular só voltam, sem regravar`() = runTest(dispatcher) {
        preferences.seen = true
        val viewModel = OnboardingViewModel(preferences)
        val effects = effects(viewModel)

        viewModel.onAction(OnboardingAction.Start)
        viewModel.onAction(OnboardingAction.Skip)

        preferences.writes shouldBe 0
        effects shouldContainExactly listOf(OnboardingEffect.Close, OnboardingEffect.Close)
    }

    @Test
    fun `tocar duas vezes no primeiro uso não leva ao Início duas vezes`() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(preferences)
        val effects = effects(viewModel)

        viewModel.onAction(OnboardingAction.Start)
        viewModel.onAction(OnboardingAction.Skip)

        preferences.writes shouldBe 1
        effects shouldContainExactly listOf(OnboardingEffect.FinishFirstUse)
    }
}
