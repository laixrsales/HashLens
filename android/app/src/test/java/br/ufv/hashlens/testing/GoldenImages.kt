package br.ufv.hashlens.testing

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream

/**
 * Imagens golden de `src/test/resources/golden/` e os valores de `expected.json`, gerados pela
 * implementação de referência `tools/golden/gerar_golden.py` (T011).
 */
object GoldenImages {
    const val ORIGINAL = "originais/20261006_205911.jpg"

    data class Entry(val file: String, val sha256: String, val pHash: String, val hammingToOriginal: Int)

    val entries: List<Entry> by lazy {
        val root = ObjectMapper().readTree(open("expected.json"))
        root["arquivos"].fields().asSequence().toList().map { (file, node) ->
            Entry(
                file = file,
                sha256 = node["sha256"].asText(),
                pHash = node["phash"].asText(),
                hammingToOriginal = node["hammingAoOriginal"].asInt()
            )
        }
    }

    fun entry(file: String): Entry = entries.single { it.file == file }

    fun open(file: String): InputStream = requireNotNull(javaClass.getResourceAsStream("/golden/$file")) {
        "recurso golden ausente: $file"
    }
}
