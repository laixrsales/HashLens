package br.ufv.hashlens.ui.components

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** 020/T054: aviso de revogação depois do vínculo, versão de lista e nota de revogação não consultada. */
class RevocationNoticeScreenshotTest : ScreenshotTest() {
    @Test
    fun all() = snapshot("RevocationNotice") { RevocationNoticeSamples.All() }
}
