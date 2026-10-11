package br.ufv.hashlens.ui.navigation

import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.domain.usecase.VerifyImageUseCase
import br.ufv.hashlens.ui.home.HomeAction
import br.ufv.hashlens.ui.verify.VerifyViewModel
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * T066: a verificação é pública (FR-019, Constituição): sem guarda de sessão, sem carteira e sem
 * custo. Falha se a rota passar pela carteira ou se a verificação passar a depender dela.
 */
class VerificationRouteTest {
    @Test
    fun `Verificar imagem no Início navega direto, sem passar pela carteira`() {
        HomeAction.VerifyImage.route() shouldBe Route.Verify
    }

    @Test
    fun `Registrar foto é a ação que passa pela carteira antes de navegar (controle)`() {
        HomeAction.RegisterPhoto.route() shouldBe null
    }

    @Test
    fun `o Resultado por registro só recebe o id do registro`() {
        Route.VerificationResult.path shouldBe "verification-result/{${VerifyViewModel.RECORD_ID}}"
        Route.verificationResult(7) shouldBe "verification-result/7"
    }

    @ParameterizedTest
    @ValueSource(classes = [VerifyViewModel::class, VerifyImageUseCase::class])
    fun `a verificação não depende de carteira, de chave do aparelho nem de transação`(type: Class<*>) {
        val dependencies = type.constructors.flatMap { it.parameterTypes.toList() }

        dependencies.filter { it in WALLET_OR_COST }.shouldBeEmpty()
    }

    private companion object {
        val WALLET_OR_COST =
            setOf(WalletSession::class.java, DeviceKeyManager::class.java, RegistryTxBuilder::class.java)
    }
}
