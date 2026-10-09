package br.ufv.hashlens.ui.home

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T038: Início sem carteira, conectado, em rede errada e com registros pendentes (ux.md §4.2). */
class HomeScreenshotTest : ScreenshotTest() {
    @Test
    fun disconnected() = screenSnapshot("Home_sem_carteira") { HomeScreen(HomeSamples.disconnected, onAction = {}) }

    @Test
    fun connected() = screenSnapshot("Home_conectada") { HomeScreen(HomeSamples.connected, onAction = {}) }

    @Test
    fun wrongNetwork() = screenSnapshot("Home_rede_errada") { HomeScreen(HomeSamples.wrongNetwork, onAction = {}) }

    @Test
    fun pending() = screenSnapshot("Home_pendentes") { HomeScreen(HomeSamples.pending, onAction = {}) }
}
