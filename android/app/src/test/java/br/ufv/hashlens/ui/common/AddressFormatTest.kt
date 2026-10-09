package br.ufv.hashlens.ui.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AddressFormatTest {
    @Test
    fun `abrevia mantendo 0x, 4 primeiros e 4 últimos dígitos`() {
        AddressFormat.short("0x12ab34cd56ef7890a1b2c3d4e5f60718293a9f3c") shouldBe "0x12ab…9f3c"
    }

    @Test
    fun `texto curto fica como está`() {
        AddressFormat.short("0x12ab9f3c") shouldBe "0x12ab9f3c"
    }
}
