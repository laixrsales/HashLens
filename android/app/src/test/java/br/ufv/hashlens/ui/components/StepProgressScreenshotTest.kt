package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T037: etapas do registro (inclusive vínculo de aparelho e falha por prazo) e da verificação. */
class StepProgressScreenshotTest : ScreenshotTest() {
    @Test
    fun registration() = snapshot("StepProgress_registro") { StepProgressSamples.Registration() }

    @Test
    fun failures() = snapshot("StepProgress_falhas") { StepProgressSamples.Failures() }

    @Test
    fun verification() = snapshot("StepProgress_verificacao") { StepProgressSamples.Verification() }
}
