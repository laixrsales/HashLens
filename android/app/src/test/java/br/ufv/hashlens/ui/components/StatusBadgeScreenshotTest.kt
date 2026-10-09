package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T038: selo de status (um por status e a contagem de pendentes do Início). */
class StatusBadgeScreenshotTest : ScreenshotTest() {
    @Test
    fun all() = snapshot("StatusBadge") { StatusBadgeSamples.All() }
}
