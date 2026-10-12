package br.ufv.hashlens.ui.navigation

import br.ufv.hashlens.ui.home.HomeAction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * T091: guarda de sessão no Início. Registrar e importar para editar não navegam direto: passam pela
 * carteira ([br.ufv.hashlens.ui.wallet.WalletGate]) e só então abrem a câmera ou o Editor. Verificar e
 * o acervo continuam abertos sem carteira (FR-019).
 */
class SessionGuardRouteTest {
    @Test
    fun `registrar e importar para editar passam pela carteira antes de navegar`() {
        HomeAction.RegisterPhoto.route() shouldBe null
        HomeAction.ImportForEdit.route() shouldBe null
    }

    @Test
    fun `verificar e o acervo não dependem da carteira`() {
        HomeAction.VerifyImage.route() shouldBe Route.Verify
        HomeAction.OpenLibrary.route() shouldBe Route.Library
    }

    @Test
    fun `o chip da carteira abre a folha ou a tela Carteira, nunca uma rota direta`() {
        HomeAction.OpenWallet.route() shouldBe null
    }
}
