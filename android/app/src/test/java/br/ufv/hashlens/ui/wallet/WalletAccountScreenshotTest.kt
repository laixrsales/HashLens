package br.ufv.hashlens.ui.wallet

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/**
 * T088: Carteira em todos os estados de ux.md §4.11 (conectada, rede errada, saldo baixo), mais o
 * saldo consultando.
 */
class WalletAccountScreenshotTest : ScreenshotTest() {
    private fun account(name: String, state: WalletAccountUiState) = screenSnapshot("Carteira_$name") {
        WalletAccountScreen(state, onAction = {})
    }

    @Test
    fun connected() = account("conectada", WalletAccountSamples.connected)

    @Test
    fun balanceLoading() = account("consultando_saldo", WalletAccountSamples.balanceLoading)

    @Test
    fun wrongNetwork() = account("rede_errada", WalletAccountSamples.wrongNetwork)

    @Test
    fun lowBalance() = account("saldo_baixo", WalletAccountSamples.lowBalance)
}
