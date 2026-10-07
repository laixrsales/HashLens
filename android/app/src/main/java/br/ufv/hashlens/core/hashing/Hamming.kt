package br.ufv.hashlens.core.hashing

import br.ufv.hashlens.domain.model.PHash
import java.math.BigDecimal
import java.math.RoundingMode

/** Distância de Hamming entre pHashes e percentual de alteração (hashing-spec §3, research R17). */
object Hamming {
    const val BITS = 64

    /** Número de bits diferentes, de 0 a [BITS]. */
    fun distance(a: PHash, b: PHash): Int = (a.value xor b.value).countOneBits()

    /** `distance / 64 × 100`, com 1 casa decimal; meio arredonda para cima (6,25 → 6,3). */
    fun percent(distance: Int): Double {
        require(distance in 0..BITS) { "distância fora de 0..$BITS: $distance" }
        // d × 100 / 64 tem expansão decimal finita, então a divisão é exata antes do arredondamento
        return BigDecimal(distance * PERCENT)
            .divide(BigDecimal(BITS))
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun percent(a: PHash, b: PHash): Double = percent(distance(a, b))

    private const val PERCENT = 100
}
