package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T037: régua de alteração (0%, valores intermediários, 100%, em relação à anterior e correspondência visual). */
class AlterationMeterScreenshotTest : ScreenshotTest() {
    @Test
    fun all() = snapshot("AlterationMeter") { AlterationMeterSamples.All() }
}
