package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T037: cabeçalho de status em todos os resultados e estados do registro. */
class StatusHeaderScreenshotTest : ScreenshotTest() {
    @Test
    fun results() = snapshot("StatusHeader_resultados") { StatusHeaderSamples.Results() }

    @Test
    fun registration() = snapshot("StatusHeader_registro") { StatusHeaderSamples.Registration() }
}
