package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T037: detalhes técnicos recolhidos e expandidos. */
class TechnicalDetailsScreenshotTest : ScreenshotTest() {
    @Test
    fun collapsed() = snapshot("TechnicalDetails_recolhido") { TechnicalDetailsSamples.Collapsed() }

    @Test
    fun expanded() = snapshot("TechnicalDetails_expandido") { TechnicalDetailsSamples.Expanded() }
}
