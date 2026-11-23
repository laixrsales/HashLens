package br.ufv.hashlens.evaluation

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.core.crypto.DeviceSignatureVerifier
import br.ufv.hashlens.core.hashing.PerceptualHasher
import br.ufv.hashlens.core.hashing.Sha256Hasher
import br.ufv.hashlens.data.attestation.DeviceOriginVerifier
import br.ufv.hashlens.data.attestation.RevocationRepository
import br.ufv.hashlens.data.chain.LogsSplitChainRpc
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.Web3jChainRpc
import br.ufv.hashlens.data.chain.Web3jEthCall
import br.ufv.hashlens.domain.model.VerificationResult
import br.ufv.hashlens.domain.usecase.VerifyImageUseCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

/**
 * Matriz de classificação do conjunto rotulado no contrato v2 (020/T061, 020 SC-007; 002 SC-001 e SC-002).
 *
 * Cada arquivo de `files/conjunto/<grupo>/` no armazenamento do app passa pela verificação real
 * ([VerifyImageUseCase] contra o contrato na Sepolia, com a configuração de `local.properties`).
 * O grupo é o rótulo esperado:
 *
 * - `originais`: idênticos a capturas registradas → "Registrada – original" (SC-001);
 * - `versoes`: idênticos a versões editadas registradas → "Registrada – versão alterada" (SC-001);
 * - `nao_registrados`: nunca registrados → "Não registrada" ou "Correspondência visual" (SC-002).
 *
 * Não há grupo de adulterados: no contrato v2 um registro com assinatura do aparelho adulterada não
 * existe, porque a rede o recusa. A 002 SC-003 passa a ser medida por essas recusas
 * (`tools/chain/tentar_origens_nao_autorizadas.py`, `docs/resultados/recusas.md`), e a classificação
 * "Registro adulterado" por dados de um leitor falso (020 SC-008, testes unitários). Se algum arquivo
 * do conjunto sair como "Registro adulterado", ele aparece na coluna e conta como erro.
 *
 * Exige ao menos 20 idênticos (originais + versões) e 20 não registrados, e 100% de acerto em cada
 * critério. Grava `evaluation/matriz-classificacao.csv` (um arquivo por linha, com
 * o tempo da verificação) e `evaluation/matriz-classificacao.md` (a matriz esperado × obtido). Sem
 * a pasta `conjunto` (ex.: no `connectedDebugAndroidTest`, que reinstala o app), o teste é pulado.
 * Roteiro em `docs/resultados/matriz-classificacao.md`.
 */
@RunWith(AndroidJUnit4::class)
class ClassificationMatrixTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Resultado como a pessoa o vê: o título da tela de resultado (contracts/verification-result.md §3). */
    private enum class Label(val title: String) {
        ORIGINAL("Registrada – original"),
        EDITED("Registrada – versão alterada"),
        VISUAL_MATCH("Correspondência visual"),
        TAMPERED("Registro adulterado"),
        NOT_REGISTERED("Não registrada"),
        UNSUPPORTED("Formato não suportado"),
        NETWORK("Não foi possível verificar")
    }

    private enum class Group(val folder: String, val criterion: String, val accepted: Set<Label>) {
        ORIGINALS("originais", "SC-001", setOf(Label.ORIGINAL)),
        VERSIONS("versoes", "SC-001", setOf(Label.EDITED)),
        NOT_REGISTERED("nao_registrados", "SC-002", setOf(Label.NOT_REGISTERED, Label.VISUAL_MATCH))
    }

    private data class Row(val file: String, val group: Group, val obtained: Label, val millis: Long) {
        val correct get() = obtained in group.accepted
    }

    @Test
    fun classifiesLabeledSet(): Unit = runBlocking {
        val root = File(context.getExternalFilesDir(null), "conjunto")
        assumeTrue("sem conjunto rotulado em $root", root.isDirectory)

        val config = ChainConfig.fromBuildConfig()
        val reader = Web3j.build(HttpService(config.rpcUrl)).let { web3j ->
            // Eventos antigos (cadeia de atestação) pelo nó com histórico, como no app
            val logs = Web3jChainRpc(Web3j.build(HttpService(config.logsRpcUrl.ifEmpty { config.rpcUrl })))
            RegistryReader(config, Web3jEthCall(web3j), LogsSplitChainRpc(Web3jChainRpc(web3j), logs), Dispatchers.IO)
        }
        val verify = VerifyImageUseCase(
            Sha256Hasher(),
            PerceptualHasher(),
            reader,
            DeviceSignatureVerifier(config),
            // 020: revalida a atestação de cada aparelho; a revogação não muda a classificação
            DeviceOriginVerifier(reader, config, RevocationRepository(OkHttpClient(), Dispatchers.IO)),
            Dispatchers.Default
        )

        val rows = Group.entries.flatMap { group ->
            val files = File(root, group.folder).listFiles { file -> file.isFile }.orEmpty().sortedBy { it.name }
            files.map { file ->
                val bytes = file.readBytes()
                val start = System.nanoTime()
                val result = verifyWithRetry(verify, bytes)
                Row(file.name, group, result.label(), (System.nanoTime() - start) / NANOS_PER_MILLI)
            }
        }
        writeReport(rows)

        val identical = rows.count { it.group == Group.ORIGINALS || it.group == Group.VERSIONS }
        assertSoftly {
            withClue("arquivos idênticos a registros (originais + versões)") { identical shouldBeGreaterThanOrEqual 20 }
            withClue("arquivos não registrados") {
                rows.count { it.group == Group.NOT_REGISTERED } shouldBeGreaterThanOrEqual
                    20
            }
            withClue(
                rows.filterNot {
                    it.correct
                }.joinToString("\n") { "${it.group.folder}/${it.file}: ${it.obtained.title}" }
            ) {
                rows.count { !it.correct } shouldBe 0
            }
        }
    }

    /** Falha de rede ou do nó não é classificação: tenta de novo antes de registrar como erro. */
    private suspend fun verifyWithRetry(verify: VerifyImageUseCase, bytes: ByteArray): VerificationResult {
        repeat(NETWORK_ATTEMPTS - 1) { attempt ->
            val result = verify(bytes)
            if (result !is VerificationResult.Error ||
                result.reason == VerificationResult.Error.Reason.UNSUPPORTED_FILE
            ) {
                return result
            }
            delay(RETRY_DELAY_MS * (attempt + 1))
        }
        return verify(bytes)
    }

    private fun VerificationResult.label(): Label = when (this) {
        is VerificationResult.RegisteredOriginal -> Label.ORIGINAL
        is VerificationResult.RegisteredEdited -> Label.EDITED
        is VerificationResult.VisualMatch -> Label.VISUAL_MATCH
        is VerificationResult.Tampered -> Label.TAMPERED
        VerificationResult.NotRegistered -> Label.NOT_REGISTERED
        is VerificationResult.Error -> when (reason) {
            VerificationResult.Error.Reason.UNSUPPORTED_FILE -> Label.UNSUPPORTED
            // Os dois aparecem como "Não foi possível verificar" (VerificationResultScreen)
            VerificationResult.Error.Reason.NETWORK, VerificationResult.Error.Reason.UNKNOWN -> Label.NETWORK
        }
    }

    private fun writeReport(rows: List<Row>) {
        val dir = File(context.getExternalFilesDir(null), "evaluation").apply { mkdirs() }
        val csv = buildString {
            appendLine("arquivo,grupo,esperado,obtido,correto,tempo_ms")
            rows.forEach {
                val expected = it.group.accepted.joinToString(" ou ") { label -> label.title }
                appendLine(
                    "\"${it.file}\",${it.group.folder},$expected,${it.obtained.title},${it.correct},${it.millis}"
                )
            }
        }
        File(dir, "matriz-classificacao.csv").writeText(csv)
        File(dir, "matriz-classificacao.md").writeText(matrix(rows))
        Log.i(TAG, csv)
    }

    /** Linhas: grupo (rótulo esperado); colunas: resultado obtido. Depois, o acerto por critério. */
    private fun matrix(rows: List<Row>): String = buildString {
        val labels = Label.entries
        appendLine("| Esperado \\ Obtido | ${labels.joinToString(" | ") { it.title }} | Total |")
        appendLine("| --- | ${labels.joinToString(" | ") { "---" }} | --- |")
        Group.entries.forEach { group ->
            val inGroup = rows.filter { it.group == group }
            val counts = labels.joinToString(" | ") { label -> inGroup.count { it.obtained == label }.toString() }
            appendLine("| ${group.folder} | $counts | ${inGroup.size} |")
        }
        appendLine()
        rows.groupBy { it.group.criterion }.toSortedMap().forEach { (criterion, inCriterion) ->
            val correct = inCriterion.count { it.correct }
            val percent = if (inCriterion.isEmpty()) 0.0 else 100.0 * correct / inCriterion.size
            appendLine(
                "- $criterion: $correct de ${inCriterion.size} corretos (${"%.1f".format(
                    Locale.forLanguageTag("pt-BR"),
                    percent
                )}%)"
            )
        }
        appendLine(
            "- SC-003 (002): no contrato v2, medido pelas recusas na rede (docs/resultados/recusas.md)"
        )
        val times = rows.filter { it.obtained != Label.NETWORK }.map { it.millis }.sorted()
        if (times.isNotEmpty()) {
            val median = times[times.size / 2]
            appendLine("- Tempo da verificação (SC-007, referência): mediana $median ms, máximo ${times.last()} ms")
        }
    }

    private companion object {
        const val TAG = "ClassificationMatrix"
        const val NETWORK_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 2_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
