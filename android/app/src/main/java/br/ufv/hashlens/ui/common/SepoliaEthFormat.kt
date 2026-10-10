package br.ufv.hashlens.ui.common

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Saldo e custo em SepoliaETH na camada principal ("saldo de teste", ux.md §6), sempre em pt-BR (R24):
 * `0,0421`, até 6 casas, sem zeros à direita.
 */
object SepoliaEthFormat {
    private const val WEI_DECIMALS = 18
    private const val MAX_DECIMALS = 6
    private val SYMBOLS = DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))

    fun fromWei(wei: BigInteger): BigDecimal = BigDecimal(wei).movePointLeft(WEI_DECIMALS)

    /**
     * @param roundUp `true` para custos (nunca mostrar menos que o estimado); `false` para saldos
     *   (nunca mostrar mais do que a carteira tem).
     */
    fun amount(value: BigDecimal, roundUp: Boolean = false): String {
        // DecimalFormat não é thread-safe: um por chamada
        val format = DecimalFormat("#,##0.######", SYMBOLS).apply {
            roundingMode = if (roundUp) RoundingMode.CEILING else RoundingMode.FLOOR
            maximumFractionDigits = MAX_DECIMALS
        }
        return format.format(value)
    }
}
