package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T037: estado vazio do acervo. */
class EmptyStateScreenshotTest : ScreenshotTest() {
    @Test
    fun library() = snapshot("EmptyState_acervo") { EmptyStateSamples.Library() }
}
