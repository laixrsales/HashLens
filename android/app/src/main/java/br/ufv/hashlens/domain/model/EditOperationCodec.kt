package br.ufv.hashlens.domain.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Serialização das operações de edição ↔ string on-chain (hashing-spec §4):
 * `op[:param,param];op[:param]`, ASCII, no máximo 256 bytes, ponto decimal em qualquer locale.
 * A lista vazia (registros originais) corresponde à string vazia.
 */
object EditOperationCodec {
    const val MAX_BYTES = 256

    private const val OPERATION_SEPARATOR = ";"
    private const val NAME_SEPARATOR = ':'
    private const val PARAM_SEPARATOR = ","

    private const val BRIGHTNESS_LIMIT = 100
    private val CONTRAST_HUNDREDTHS = 50..200
    private val BLUR_KERNEL = 3..25
    private val EDGE_THRESHOLD = 0..255
    private val ROTATE_TURNS = 1..3
    private const val EDGES_PARAMS = 2
    private const val CROP_PARAMS = 4
    private const val HUNDREDTHS = 100
    private const val THOUSANDTHS = 1000
    private val PRINTABLE_ASCII = 0x20..0x7e

    /** @throws IllegalArgumentException se alguma operação estiver fora da faixa ou o texto passar de 256 bytes. */
    fun encode(operations: List<EditOperation>): String {
        operations.forEach(::validate)
        val text = operations.joinToString(OPERATION_SEPARATOR, transform = ::encodeOne)
        require(text.length <= MAX_BYTES) { "operações passam de $MAX_BYTES bytes: ${text.length}" }
        return text
    }

    /** @throws IllegalArgumentException se o texto estiver malformado ou fora das faixas. */
    fun decode(text: String): List<EditOperation> {
        require(text.length <= MAX_BYTES) { "operações passam de $MAX_BYTES bytes: ${text.length}" }
        require(text.all { it.code in PRINTABLE_ASCII }) { "operações devem ser ASCII imprimível" }
        if (text.isEmpty()) return emptyList()
        return text.split(OPERATION_SEPARATOR).map { decodeOne(it).also(::validate) }
    }

    private fun encodeOne(operation: EditOperation): String = when (operation) {
        is EditOperation.Brightness -> "brightness:" + String.format(Locale.ROOT, "%+d", operation.delta)
        is EditOperation.Contrast -> "contrast:" + String.format(Locale.ROOT, "%.2f", operation.factor)
        EditOperation.Grayscale -> "grayscale"
        EditOperation.Sepia -> "sepia"
        is EditOperation.Blur -> "blur:${operation.kernel}"
        EditOperation.Sharpen -> "sharpen"
        is EditOperation.Edges -> "edges:${operation.low},${operation.high}"
        is EditOperation.Crop -> "crop:" + listOf(operation.x, operation.y, operation.w, operation.h)
            .joinToString(PARAM_SEPARATOR) { String.format(Locale.ROOT, "%.3f", it) }
        is EditOperation.Rotate90 -> "rotate90:${operation.turns}"
    }

    private fun decodeOne(text: String): EditOperation {
        val name = text.substringBefore(NAME_SEPARATOR)
        val params = Params(
            if (NAME_SEPARATOR in text) text.substringAfter(NAME_SEPARATOR).split(PARAM_SEPARATOR) else emptyList()
        )
        return when (name) {
            "brightness" -> EditOperation.Brightness(params.ints(1)[0])
            "contrast" -> EditOperation.Contrast(params.floats(1)[0])
            "grayscale" -> params.none(EditOperation.Grayscale)
            "sepia" -> params.none(EditOperation.Sepia)
            "blur" -> EditOperation.Blur(params.ints(1)[0])
            "sharpen" -> params.none(EditOperation.Sharpen)
            "edges" -> params.ints(EDGES_PARAMS).let { EditOperation.Edges(it[0], it[1]) }
            "crop" -> params.floats(CROP_PARAMS).let { EditOperation.Crop(it[0], it[1], it[2], it.last()) }
            "rotate90" -> EditOperation.Rotate90(params.ints(1)[0])
            else -> throw IllegalArgumentException("operação desconhecida: '$name'")
        }
    }

    private fun validate(operation: EditOperation) {
        when (operation) {
            is EditOperation.Brightness ->
                require(operation.delta in -BRIGHTNESS_LIMIT..BRIGHTNESS_LIMIT) { "brilho fora da faixa: $operation" }
            is EditOperation.Contrast ->
                require(scaled(operation.factor, HUNDREDTHS) in CONTRAST_HUNDREDTHS) {
                    "contraste fora da faixa: $operation"
                }
            is EditOperation.Blur ->
                require(operation.kernel in BLUR_KERNEL && operation.kernel % 2 == 1) { "kernel inválido: $operation" }
            is EditOperation.Edges ->
                require(operation.low in EDGE_THRESHOLD && operation.high in EDGE_THRESHOLD) {
                    "limiares fora da faixa: $operation"
                }
            is EditOperation.Crop -> validateCrop(operation)
            is EditOperation.Rotate90 ->
                require(operation.turns in ROTATE_TURNS) { "rotação fora da faixa: $operation" }
            EditOperation.Grayscale, EditOperation.Sepia, EditOperation.Sharpen -> Unit
        }
    }

    /** Compara em milésimos, a precisão serializada, para não depender de arredondamento de `Float`. */
    private fun validateCrop(crop: EditOperation.Crop) {
        val x = scaled(crop.x, THOUSANDTHS)
        val y = scaled(crop.y, THOUSANDTHS)
        val w = scaled(crop.w, THOUSANDTHS)
        val h = scaled(crop.h, THOUSANDTHS)
        require(x >= 0 && y >= 0 && w > 0 && h > 0 && x + w <= THOUSANDTHS && y + h <= THOUSANDTHS) {
            "recorte fora da imagem: $crop"
        }
    }

    /** Valor em centésimos ou milésimos; não finito vira -1 (sempre fora da faixa). */
    private fun scaled(value: Float, factor: Int): Int = if (value.isFinite()) (value * factor).roundToInt() else -1

    /** Parâmetros de uma operação, já separados por vírgula. */
    private class Params(private val values: List<String>) {
        fun <T> none(operation: T): T {
            require(values.isEmpty()) { "operação sem parâmetros recebeu: $values" }
            return operation
        }

        fun ints(count: Int): List<Int> =
            exactly(count).map { requireNotNull(it.toIntOrNull()) { "número inteiro inválido: '$it'" } }

        fun floats(count: Int): List<Float> = exactly(count).map {
            requireNotNull(it.toBigDecimalOrNull()?.toFloat()) { "número decimal inválido: '$it'" }
        }

        private fun exactly(count: Int): List<String> {
            require(values.size == count && values.all { it.isNotEmpty() }) { "esperados $count parâmetros: $values" }
            return values
        }
    }
}
