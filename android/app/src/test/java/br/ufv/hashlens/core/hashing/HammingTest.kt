package br.ufv.hashlens.core.hashing

import br.ufv.hashlens.domain.model.PHash
import br.ufv.hashlens.testing.GoldenImages
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

/** T014: hashing-spec §3 (distância de Hamming e percentual com 1 casa decimal). */
class HammingTest {
    private val sample = PHash(0x9e4c398ab9d93331uL)

    @Test
    fun `hashes iguais têm distância 0`() {
        Hamming.distance(sample, sample) shouldBe 0
    }

    @Test
    fun `hashes complementares têm distância 64`() {
        Hamming.distance(PHash(0uL), PHash(ULong.MAX_VALUE)) shouldBe 64
        Hamming.distance(sample, PHash(sample.value.inv())) shouldBe 64
    }

    @Test
    fun `um bit diferente no extremo mais e menos significativo dá distância 1`() {
        Hamming.distance(PHash(0uL), PHash(1uL shl 63)) shouldBe 1
        Hamming.distance(PHash(0uL), PHash(1uL)) shouldBe 1
    }

    @Test
    fun `distância é simétrica`() {
        val other = PHash(0x0123456789abcdefuL)

        Hamming.distance(sample, other) shouldBe Hamming.distance(other, sample)
    }

    @ParameterizedTest(name = "d={0} → {1}%")
    @CsvSource(
        "0, 0.0",
        "64, 100.0",
        "32, 50.0",
        "1, 1.6", // 1,5625
        "3, 4.7", // 4,6875
        "4, 6.3", // 6,25: meio arredonda para cima
        "38, 59.4" // 59,375
    )
    fun `percentual é d sobre 64 vezes 100 arredondado a 1 casa`(distance: Int, expected: Double) {
        Hamming.percent(distance) shouldBe expected
    }

    @Test
    fun `percentual entre dois hashes usa a distância entre eles`() {
        Hamming.percent(PHash(0uL), PHash(ULong.MAX_VALUE)) shouldBe 100.0
    }

    @ParameterizedTest
    @CsvSource("-1", "65")
    fun `distância fora de 0 a 64 é rejeitada`(distance: Int) {
        shouldThrow<IllegalArgumentException> { Hamming.percent(distance) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFiles")
    fun `distâncias golden ao original batem com expected json`(file: String) {
        val original = PHash.fromHex(GoldenImages.entry(GoldenImages.ORIGINAL).pHash)
        val entry = GoldenImages.entry(file)

        Hamming.distance(PHash.fromHex(entry.pHash), original) shouldBe entry.hammingToOriginal
    }

    companion object {
        @JvmStatic
        fun goldenFiles() = GoldenImages.entries.map { it.file }
    }
}
