package br.ufv.hashlens.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.Locale
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** T017: hashing-spec §4 (serialização das operações de edição, máx. 256 bytes ASCII). */
class EditOperationCodecTest {
    private val defaultLocale = Locale.getDefault()

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private val allOperations = listOf(
        EditOperation.Brightness(30),
        EditOperation.Brightness(-15),
        EditOperation.Contrast(1.25f),
        EditOperation.Grayscale,
        EditOperation.Sepia,
        EditOperation.Blur(7),
        EditOperation.Sharpen,
        EditOperation.Edges(50, 150),
        EditOperation.Crop(0.1f, 0.05f, 0.8f, 0.9f),
        EditOperation.Rotate90(1)
    )
    private val allOperationsText =
        "brightness:+30;brightness:-15;contrast:1.25;grayscale;sepia;blur:7;sharpen;edges:50,150;" +
            "crop:0.100,0.050,0.800,0.900;rotate90:1"

    @Test
    fun `serializa todas as operações na ordem aplicada`() {
        EditOperationCodec.encode(allOperations) shouldBe allOperationsText
    }

    @Test
    fun `ida e volta de todas as operações`() {
        EditOperationCodec.decode(EditOperationCodec.encode(allOperations)) shouldBe allOperations
    }

    @Test
    fun `contraste usa 2 casas e recorte 3 casas`() {
        EditOperationCodec.encode(listOf(EditOperation.Contrast(0.5f))) shouldBe "contrast:0.50"
        EditOperationCodec.encode(listOf(EditOperation.Contrast(2f))) shouldBe "contrast:2.00"
        EditOperationCodec.encode(listOf(EditOperation.Crop(0f, 0f, 1f, 1f))) shouldBe "crop:0.000,0.000,1.000,1.000"
    }

    @Test
    fun `brilho sempre leva sinal`() {
        EditOperationCodec.encode(listOf(EditOperation.Brightness(100))) shouldBe "brightness:+100"
        EditOperationCodec.encode(listOf(EditOperation.Brightness(-100))) shouldBe "brightness:-100"
    }

    @Test
    fun `locale pt-BR continua serializando com ponto`() {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))

        EditOperationCodec.encode(allOperations) shouldBe allOperationsText
        EditOperationCodec.decode(allOperationsText) shouldBe allOperations
    }

    @Test
    fun `lista vazia (registros originais) vira string vazia e vice-versa`() {
        EditOperationCodec.encode(emptyList()) shouldBe ""
        EditOperationCodec.decode("") shouldBe emptyList()
    }

    @Test
    fun `aceita exatamente 256 bytes e rejeita 257`() {
        val atLimit = List(25) { EditOperation.Grayscale } + EditOperation.Blur(3) // 249 + 7
        val overLimit = List(25) { EditOperation.Grayscale } + EditOperation.Blur(11) // 249 + 8

        EditOperationCodec.encode(atLimit).length shouldBe 256
        shouldThrow<IllegalArgumentException> { EditOperationCodec.encode(overLimit) }
        shouldThrow<IllegalArgumentException> {
            EditOperationCodec.decode(List(25) { "grayscale" }.joinToString(";") + ";blur:11")
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            ";", // operação vazia
            "grayscale;", // separador sobrando
            "grayscale;;sepia", // operação vazia no meio
            "vignette", // operação desconhecida
            "Grayscale", // nome com maiúscula
            "grayscale:1", // parâmetro em operação sem parâmetros
            "brightness", // parâmetro ausente
            "brightness:", // parâmetro vazio
            "brightness:abc", // não numérico
            "brightness:+101", // fora de -100..+100
            "contrast:1,25", // vírgula decimal
            "contrast:0.49", // fora de 0.50..2.00
            "contrast:2.01",
            "blur:4", // kernel par
            "blur:1", // fora de 3..25
            "blur:27",
            "edges:50", // falta um parâmetro
            "edges:-1,150", // fora de 0..255
            "edges:50,256",
            "crop:0.100,0.100,0.800", // falta um parâmetro
            "crop:0.500,0.000,0.600,1.000", // x + w > 1
            "crop:0.000,0.000,0.000,1.000", // largura zero
            "rotate90:0", // fora de 1..3
            "rotate90:4",
            "sepia:" // dois-pontos sem parâmetro
        ]
    )
    fun `entrada malformada é rejeitada`(text: String) {
        shouldThrow<IllegalArgumentException> { EditOperationCodec.decode(text) }
    }

    @Test
    fun `operação fora da faixa também é rejeitada ao serializar`() {
        shouldThrow<IllegalArgumentException> { EditOperationCodec.encode(listOf(EditOperation.Blur(4))) }
        shouldThrow<IllegalArgumentException> { EditOperationCodec.encode(listOf(EditOperation.Brightness(101))) }
    }
}
