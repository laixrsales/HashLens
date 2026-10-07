package br.ufv.hashlens.core.hashing

import br.ufv.hashlens.testing.GoldenImages
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** T012: hashing-spec §1 (SHA-256 de todos os bytes do arquivo, hex minúsculo). */
class Sha256HasherTest {
    private val hasher = Sha256Hasher()

    @ParameterizedTest(name = "{0}")
    @MethodSource("nistVectors")
    fun `vetores NIST FIPS 180-2`(input: ByteArray, expected: String) {
        hasher.hash(ByteArrayInputStream(input)).hex shouldBe expected
    }

    @Test
    fun `resultado em hex minúsculo de 64 caracteres`() {
        hasher.hash(ByteArrayInputStream("HashLens".toByteArray())).hex shouldMatch Regex("[0-9a-f]{64}")
    }

    @Test
    fun `leitura em blocos pequenos dá o mesmo resultado que a leitura direta`() {
        val bytes = ByteArray(100_003) { (it * 31).toByte() }

        val direct = hasher.hash(ByteArrayInputStream(bytes))
        val chunked = hasher.hash(TrickleInputStream(bytes, maxChunk = 7))

        chunked shouldBe direct
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFiles")
    fun `arquivos golden batem com expected json`(file: String) {
        val expected = GoldenImages.entry(file).sha256

        GoldenImages.open(file).use { hasher.hash(it).hex shouldBe expected }
    }

    /** Devolve no máximo [maxChunk] bytes por leitura, como streams de rede ou de `ContentResolver`. */
    private class TrickleInputStream(bytes: ByteArray, private val maxChunk: Int) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, minOf(len, maxChunk))
    }

    companion object {
        @JvmStatic
        fun nistVectors() = listOf(
            Arguments.of(
                named("mensagem vazia", ByteArray(0)),
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            ),
            Arguments.of(
                named("abc", "abc".toByteArray()),
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            ),
            Arguments.of(
                named("448 bits", "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".toByteArray()),
                "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
            ),
            Arguments.of(
                named("um milhão de 'a'", ByteArray(1_000_000) { 'a'.code.toByte() }),
                "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"
            )
        )

        @JvmStatic
        fun goldenFiles() = GoldenImages.entries.map { it.file }
    }
}
