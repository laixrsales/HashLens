package br.ufv.hashlens.ui.navigation

import br.ufv.hashlens.core.crypto.DeviceKeyManager
import br.ufv.hashlens.data.chain.RegistryTxBuilder
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.domain.usecase.TraceGenealogyUseCase
import br.ufv.hashlens.ui.genealogy.GenealogyViewModel
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * T084: o Histórico da imagem é aberto pelo id do registro (Resultado da verificação, outra versão
 * da árvore e, na US6, o acervo) e, como a verificação, é público: sem carteira e sem custo.
 */
class GenealogyRouteTest {
    @Test
    fun `o Histórico só recebe o id do registro`() {
        Route.Genealogy.path shouldBe "genealogy/{${GenealogyViewModel.RECORD_ID}}"
        Route.genealogy(7) shouldBe "genealogy/7"
    }

    @ParameterizedTest
    @ValueSource(classes = [GenealogyViewModel::class, TraceGenealogyUseCase::class])
    fun `o Histórico não depende de carteira, de chave do aparelho nem de transação`(type: Class<*>) {
        val dependencies = type.constructors.flatMap { it.parameterTypes.toList() }

        dependencies.filter { it in WALLET_OR_COST }.shouldBeEmpty()
    }

    private companion object {
        val WALLET_OR_COST =
            setOf(WalletSession::class.java, DeviceKeyManager::class.java, RegistryTxBuilder::class.java)
    }
}
