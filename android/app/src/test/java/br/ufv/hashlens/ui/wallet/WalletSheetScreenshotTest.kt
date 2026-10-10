package br.ufv.hashlens.ui.wallet

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/** T055: folha "Conectar carteira" em todos os estados de ux.md §4.3. */
class WalletSheetScreenshotTest : ScreenshotTest() {
    @Test
    fun initial() = screenSnapshot("WalletSheet_inicial") { WalletSheetFrame(WalletSheetSamples.initial) }

    @Test
    fun connecting() = screenSnapshot("WalletSheet_aguardando") { WalletSheetFrame(WalletSheetSamples.connecting) }

    @Test
    fun connected() = screenSnapshot("WalletSheet_conectada") { WalletSheetFrame(WalletSheetSamples.connected) }

    @Test
    fun wrongNetwork() = screenSnapshot("WalletSheet_rede_errada") { WalletSheetFrame(WalletSheetSamples.wrongNetwork) }

    @Test
    fun lowBalance() = screenSnapshot("WalletSheet_saldo_insuficiente") {
        WalletSheetFrame(WalletSheetSamples.lowBalance)
    }

    @Test
    fun notInstalled() = screenSnapshot("WalletSheet_sem_carteira") {
        WalletSheetFrame(WalletSheetSamples.notInstalled)
    }

    @Test
    fun rejected() = screenSnapshot("WalletSheet_recusada") { WalletSheetFrame(WalletSheetSamples.rejected) }
}
