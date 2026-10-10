package br.ufv.hashlens.ui.common

import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Test

/** Saldo e custo em SepoliaETH na camada principal, em pt-BR (R24). */
class SepoliaEthFormatTest {
    @Test
    fun `converte wei para SepoliaETH`() {
        SepoliaEthFormat.fromWei(BigInteger.TEN.pow(18)) shouldBeEqualComparingTo BigDecimal.ONE
        SepoliaEthFormat.fromWei(BigInteger.valueOf(412_300_000_000_000)) shouldBeEqualComparingTo
            BigDecimal("0.0004123")
    }

    @Test
    fun `usa vírgula decimal e sem zeros à direita`() {
        SepoliaEthFormat.amount(BigDecimal("0.0421")) shouldBe "0,0421"
        SepoliaEthFormat.amount(BigDecimal("0.00002")) shouldBe "0,00002"
        SepoliaEthFormat.amount(BigDecimal("0.500000")) shouldBe "0,5"
        SepoliaEthFormat.amount(BigDecimal.ZERO) shouldBe "0"
    }

    @Test
    fun `separa milhares com ponto`() {
        SepoliaEthFormat.amount(BigDecimal("1234.5")) shouldBe "1.234,5"
    }

    @Test
    fun `saldo arredonda para baixo em 6 casas`() {
        // Nunca mostrar mais saldo do que a carteira tem
        SepoliaEthFormat.amount(BigDecimal("0.0004127")) shouldBe "0,000412"
        SepoliaEthFormat.amount(BigDecimal("0.0000004")) shouldBe "0"
    }

    @Test
    fun `custo arredonda para cima em 6 casas`() {
        // Nunca mostrar um custo menor que o estimado
        SepoliaEthFormat.amount(BigDecimal("0.0004121"), roundUp = true) shouldBe "0,000413"
        SepoliaEthFormat.amount(BigDecimal("0.0000004"), roundUp = true) shouldBe "0,000001"
    }
}
