package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T037: chip da carteira desconectada, conectada e em rede errada. */
class AddressChipScreenshotTest : ScreenshotTest() {
    @Test
    fun all() = snapshot("AddressChip") { AddressChipSamples.All() }
}
