package br.ufv.hashlens.ui.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.ufv.hashlens.ui.theme.HashLensTheme
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tela Carteira no aparelho (T091, ux.md §4.11): a conta, a rede e o saldo, "Desconectar" com a nota
 * de que fotos e registros continuam, copiar o endereço e a ação de cada problema da conta.
 */
@RunWith(AndroidJUnit4::class)
class WalletAccountScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<WalletAccountAction>()

    private fun show(state: WalletAccountUiState) = compose.setContent {
        HashLensTheme { WalletAccountScreen(state, onAction = { actions += it }) }
    }

    @Test
    fun desconectarAvisaQueFotosERegistrosContinuam() {
        show(WalletAccountSamples.connected)

        compose.onNodeWithText("Sepolia, rede de testes").assertIsDisplayed()
        compose.onNodeWithText("0,0421 SepoliaETH").assertIsDisplayed()
        compose.onNodeWithText(
            "Ao desconectar, as fotos e os registros continuam no aparelho e na rede. " +
                "Para registrar ou editar, conecte de novo."
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Desconectar").performScrollTo().performClick()

        actions shouldContainExactly listOf(WalletAccountAction.Disconnect)
    }

    @Test
    fun copiarOEnderecoCompleto() {
        show(WalletAccountSamples.connected)

        compose.onNodeWithContentDescription("Copiar endereço completo").performClick()

        actions shouldContainExactly listOf(WalletAccountAction.CopyAddress)
    }

    @Test
    fun redeErradaPedeATrocaParaSepolia() {
        show(WalletAccountSamples.wrongNetwork)

        compose.onNodeWithText("Rede errada").assertIsDisplayed()
        compose.onNodeWithText("Trocar para Sepolia").performScrollTo().performClick()

        actions shouldContainExactly listOf(WalletAccountAction.SwitchToSepolia)
    }

    @Test
    fun saldoBaixoLevaAoFaucet() {
        show(WalletAccountSamples.lowBalance)

        compose.onNodeWithText("Obter saldo de teste").performScrollTo().performClick()

        actions shouldContainExactly listOf(WalletAccountAction.OpenFaucet)
    }

    @Test
    fun semRedeDizQueNaoConsultouOSaldo() {
        show(WalletAccountSamples.balanceUnavailable)

        compose.onNodeWithText("Sem conexão para consultar o saldo agora.").assertIsDisplayed()
    }
}
