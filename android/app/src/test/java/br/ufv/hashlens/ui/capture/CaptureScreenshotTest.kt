package br.ufv.hashlens.ui.capture

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T055: Câmera em todos os estados de ux.md §4.4. */
class CaptureScreenshotTest : ScreenshotTest() {
    @Test
    fun ready() = screenSnapshot("Captura_pronta") { CaptureSamples.Ready() }

    @Test
    fun processing() = screenSnapshot("Captura_processando") { CaptureSamples.Processing() }

    @Test
    fun permissionDenied() = screenSnapshot("Captura_sem_permissao") { CaptureSamples.PermissionDenied() }

    @Test
    fun permissionPermanentlyDenied() =
        screenSnapshot("Captura_sem_permissao_configuracoes") { CaptureSamples.PermissionPermanentlyDenied() }

    @Test
    fun error() = screenSnapshot("Captura_erro") { CaptureSamples.Error() }
}
