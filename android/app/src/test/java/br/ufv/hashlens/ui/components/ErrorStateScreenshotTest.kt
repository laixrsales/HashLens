package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T037: erros de rede, arquivo não suportado e câmera. */
class ErrorStateScreenshotTest : ScreenshotTest() {
    @Test
    fun network() = snapshot("ErrorState_rede") { ErrorStateSamples.Network() }

    @Test
    fun unsupportedFile() = snapshot("ErrorState_formato") { ErrorStateSamples.UnsupportedFile() }

    @Test
    fun camera() = snapshot("ErrorState_camera") { ErrorStateSamples.Camera() }
}
