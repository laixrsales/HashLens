package br.ufv.hashlens.evaluation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.ufv.hashlens.ui.capture.CaptureSamples
import br.ufv.hashlens.ui.capture.RegistrationProgressSamples
import br.ufv.hashlens.ui.capture.RegistrationProgressScreen
import br.ufv.hashlens.ui.editor.EditorSamples
import br.ufv.hashlens.ui.editor.EditorScreen
import br.ufv.hashlens.ui.genealogy.GenealogySamples
import br.ufv.hashlens.ui.genealogy.GenealogyScreen
import br.ufv.hashlens.ui.home.HomeSamples
import br.ufv.hashlens.ui.home.HomeScreen
import br.ufv.hashlens.ui.library.LibraryItemSheetFrame
import br.ufv.hashlens.ui.library.LibrarySamples
import br.ufv.hashlens.ui.library.LibraryScreen
import br.ufv.hashlens.ui.onboarding.OnboardingScreen
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.theme.HashLensTheme
import br.ufv.hashlens.ui.verify.VerificationResultSamples
import br.ufv.hashlens.ui.verify.VerificationResultScreen
import br.ufv.hashlens.ui.verify.VerifySamples
import br.ufv.hashlens.ui.verify.VerifyScreen
import br.ufv.hashlens.ui.wallet.WalletAccountSamples
import br.ufv.hashlens.ui.wallet.WalletAccountScreen
import br.ufv.hashlens.ui.wallet.WalletSheetFrame
import br.ufv.hashlens.ui.wallet.WalletSheetSamples
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File
import java.util.Locale
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Auditoria de acessibilidade (T097, FR-036, ux.md §7) com o Accessibility Test Framework, o motor
 * do Accessibility Scanner: rótulos, alvos de toque, contraste de texto e de componentes (medido na
 * captura da tela), texto cortado e itens duplicados.
 *
 * Cada estado de cada tela (as amostras dos screenshot tests) é desenhado no aparelho em tema
 * claro, escuro e com fonte em 200%, e checado no topo e a cada página de rolagem. Falha se houver
 * algum resultado ERROR; ERROR e WARNING vão para `evaluation/acessibilidade.csv` no diretório de
 * arquivos do app (ver `docs/resultados/acessibilidade.md`).
 *
 * Usa a UiAutomation, como o Scanner, e não `enableAccessibilityChecks` do Compose, que exige
 * API 34 (o aparelho de testes tem Android 13).
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityAuditTest {
    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private enum class Variant(val label: String, val dark: Boolean, val fontScale: Float) {
        Light("claro", dark = false, fontScale = 1f),
        Dark("escuro", dark = true, fontScale = 1f),
        LargeFont("fonte200", dark = false, fontScale = 2f)
    }

    private class State(val name: String, val content: @Composable () -> Unit)

    private data class Finding(
        val screen: String,
        val state: String,
        val variant: String,
        val page: Int,
        val type: AccessibilityCheckResultType,
        val check: String,
        val element: String,
        val message: String
    )

    @Test
    fun onboarding() = audit("Como funciona", State("unico") { OnboardingScreen(onStart = {}, onSkip = {}) })

    @Test
    fun home() = audit(
        "Inicio",
        State("sem_carteira") { HomeScreen(HomeSamples.disconnected, onAction = {}) },
        State("conectada") { HomeScreen(HomeSamples.connected, onAction = {}) },
        State("rede_errada") { HomeScreen(HomeSamples.wrongNetwork, onAction = {}) },
        State("pendentes") { HomeScreen(HomeSamples.pending, onAction = {}) }
    )

    @Test
    fun walletSheet() = audit(
        "Conectar carteira",
        State("inicial") { WalletSheetFrame(WalletSheetSamples.initial) },
        State("aguardando") { WalletSheetFrame(WalletSheetSamples.connecting) },
        State("conectada") { WalletSheetFrame(WalletSheetSamples.connected) },
        State("rede_errada") { WalletSheetFrame(WalletSheetSamples.wrongNetwork) },
        State("saldo_insuficiente") { WalletSheetFrame(WalletSheetSamples.lowBalance) },
        State("sem_carteira") { WalletSheetFrame(WalletSheetSamples.notInstalled) },
        State("recusada") { WalletSheetFrame(WalletSheetSamples.rejected) }
    )

    @Test
    fun capture() = audit(
        "Camera",
        State("pronta") { CaptureSamples.Ready() },
        State("processando") { CaptureSamples.Processing() },
        State("sem_permissao") { CaptureSamples.PermissionDenied() },
        State("sem_permissao_configuracoes") { CaptureSamples.PermissionPermanentlyDenied() },
        State("erro") { CaptureSamples.Error() }
    )

    @Test
    fun registrationProgress() = with(RegistrationProgressSamples) {
        audit(
            "Acompanhamento do registro",
            *listOf(
                "vinculando_aparelho" to bindingDevice,
                "novo_vinculo" to newDeviceKey,
                "aguardando_aprovacao" to awaitingApproval,
                "pendente" to pending,
                "confirmado" to confirmed,
                "rejeitado" to rejected,
                "falha_rede" to networkFailure,
                "falha_prazo" to timeout,
                "falha_releitura" to hashMismatch,
                "falha_revertida" to reverted,
                "falha_galeria" to saveFailed,
                "saldo_insuficiente" to insufficientFunds,
                "ja_registrada" to alreadyRegistered
            ).map { (name, state) ->
                State(name) { RegistrationProgressScreen(state, onAction = {}, zone = SampleData.ZONE) }
            }.toTypedArray()
        )
    }

    @Test
    fun editor() = with(EditorSamples) {
        audit(
            "Editor",
            *listOf(
                "carregando" to loading,
                "editando" to editing,
                "sem_alteracoes" to noChanges,
                "ajuste_brilho" to adjustingBrightness,
                "recorte" to cropping,
                "antes_depois" to comparing,
                "imagem_de_terceiro" to thirdParty,
                "aviso_permanencia" to confirming,
                "processando" to processing,
                "nao_elegivel_pendente" to pending,
                "nao_elegivel_registro_falhou" to registrationFailed,
                "nao_elegivel_sem_registro" to notRegistered,
                "nao_elegivel_adulterado" to tampered,
                "importacao_sem_conexao" to importFailed,
                "nao_elegivel_arquivo" to unavailable
            ).map { (name, state) -> State(name) { EditorScreen(state, onAction = {}) } }.toTypedArray()
        )
    }

    @Test
    fun verify() = with(VerifySamples) {
        audit(
            "Verificar imagem",
            *listOf("inicial" to idle, "lendo" to reading, "consultando" to querying, "nao_suportado" to unsupported)
                .map { (name, state) -> State(name) { VerifyScreen(state, onAction = {}) } }
                .toTypedArray()
        )
    }

    @Test
    fun verificationResult() = with(VerificationResultSamples) {
        audit(
            "Resultado da verificacao",
            *listOf(
                "original" to original,
                "versao_alterada" to edited,
                "versao_alterada_zero" to editedZero,
                "correspondencia_visual" to visualMatch,
                "adulterado" to tampered,
                "nao_registrada" to notRegistered,
                "sem_conexao" to network
            ).map { (name, state) ->
                State(name) { VerificationResultScreen(state, onAction = {}, zone = SampleData.ZONE) }
            }.toTypedArray()
        )
    }

    @Test
    fun genealogy() = with(GenealogySamples) {
        audit(
            "Historico da imagem",
            *listOf(
                "carregando" to loading,
                "cadeia_simples" to simpleChain,
                "com_ramificacoes" to withBranches,
                "ramificacoes_abertas" to branchesExpanded,
                "limite_excedido" to truncated,
                "assinatura_invalida" to invalidSignature,
                "sem_conexao" to network
            ).map { (name, state) ->
                State(name) { GenealogyScreen(state, onAction = {}, zone = SampleData.ZONE) }
            }.toTypedArray()
        )
    }

    @Test
    fun library() = with(LibrarySamples) {
        val screens = listOf(
            "carregando" to loading,
            "vazio" to empty,
            "com_itens" to withItems,
            "pendentes" to pending,
            "falhas" to failed,
            "falhas_vazio" to failedEmpty,
            "arquivo_removido" to fileRemoved
        ).map { (name, state) -> State(name) { LibraryScreen(state, onAction = {}, zone = SampleData.ZONE) } }
        val sheets = listOf(
            "opcoes_registrada" to sheetConfirmed,
            "opcoes_registrando" to sheetPending,
            "opcoes_falhou" to sheetFailed,
            "opcoes_arquivo_removido" to sheetFileRemoved
        ).map { (name, item) -> State(name) { LibraryItemSheetFrame(item, SampleData.ZONE) } }
        audit("Meu acervo", *(screens + sheets).toTypedArray())
    }

    @Test
    fun walletAccount() = with(WalletAccountSamples) {
        audit(
            "Carteira",
            *listOf(
                "conectada" to connected,
                "consultando_saldo" to balanceLoading,
                "saldo_indisponivel" to balanceUnavailable,
                "rede_errada" to wrongNetwork,
                "saldo_baixo" to lowBalance
            ).map { (name, state) -> State(name) { WalletAccountScreen(state, onAction = {}) } }.toTypedArray()
        )
    }

    /**
     * Controle positivo: uma tela com um botão de 16 dp sem rótulo e texto cinza-claro sobre branco
     * tem de ser reprovada, senão as checagens não estão enxergando a tela.
     */
    @Test
    fun checksDetectViolations() {
        val found = collect(
            CONTROL,
            State("ruim") {
                Column(Modifier.padding(top = 48.dp)) {
                    Box(Modifier.size(16.dp).background(Color.Black).clickable {})
                    Text("Texto de baixo contraste", color = Color(0xFFDDDDDD), fontSize = 14.sp)
                }
            },
            variants = listOf(Variant.Light)
        )
        // Contraste medido na captura sai como WARNING; alvo de toque e rótulo, como ERROR
        val checks = found.map { it.check }.toSet()
        withClue(found.joinToString("\n") { "${it.check} ${it.element}" }) {
            checks shouldContainAll setOf("TouchTargetSizeCheck", "SpeakableTextPresentCheck", "TextContrastCheck")
        }
    }

    private fun audit(screen: String, vararg states: State) {
        val found = collect(screen, *states)
        findings += found
        val errors = found.filter { it.type == AccessibilityCheckResultType.ERROR }
        withClue(
            errors.joinToString("\n") {
                "${it.state}/${it.variant}/p${it.page}: ${it.check} ${it.element}: ${it.message}"
            }
        ) {
            errors.shouldBeEmpty()
        }
    }

    private fun collect(
        screen: String,
        vararg states: State,
        variants: List<Variant> = Variant.entries
    ): List<Finding> {
        // Conecta a UiAutomation antes de desenhar: é ela que liga a árvore de acessibilidade do Compose
        val uiAutomation = instrumentation.uiAutomation
        // Relógio manual, como nos screenshot tests: os indicadores de progresso nunca deixariam a tela ociosa
        compose.mainClock.autoAdvance = false
        val current = mutableStateOf<Pair<State, Variant>?>(null)
        compose.setContent {
            val (state, variant) = current.value ?: return@setContent
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
                HashLensTheme(darkTheme = variant.dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        // Estado novo a cada combinação (rolagem, seções abertas)
                        key(state.name, variant) { state.content() }
                    }
                }
            }
        }
        val found = mutableListOf<Finding>()
        for (state in states) {
            for (variant in variants) {
                compose.runOnIdle { current.value = state to variant }
                settle()
                var page = 0
                do {
                    val hierarchy = AccessibilityHierarchyAndroid
                        .newBuilder(checkNotNull(uiAutomation.rootInActiveWindow), instrumentation.targetContext)
                        .build()
                    // Sem textos, a UiAutomation não enxergou a árvore do Compose e as checagens não valeriam nada
                    val views = hierarchy.activeWindow.allViews
                    val labeled = views.count { !it.text.isNullOrEmpty() || !it.contentDescription.isNullOrEmpty() }
                    withClue("${state.name}/${variant.label}/p$page: árvore de acessibilidade sem textos") {
                        labeled shouldBeGreaterThan 0
                    }
                    // Títulos marcados para o TalkBack (ux.md §7): o ATF não checa, então só contamos
                    val headings = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                        .fetchSemanticsNodes()
                        .size
                    if (screen != CONTROL) {
                        coverage += "$screen,${state.name},${variant.label},$page,${views.size},$labeled,$headings"
                    }
                    val parameters = Parameters().apply { putScreenCapture(BitmapImage(uiAutomation.takeScreenshot())) }
                    CHECKS.flatMap { it.runCheckOnHierarchy(hierarchy, null, parameters) }
                        .filter {
                            it.type == AccessibilityCheckResultType.ERROR ||
                                it.type == AccessibilityCheckResultType.WARNING
                        }
                        .mapTo(found) { result ->
                            Finding(
                                screen = screen,
                                state = state.name,
                                variant = variant.label,
                                page = page,
                                type = result.type,
                                check = result.sourceCheckClass.simpleName,
                                element = result.element?.let { e ->
                                    val label = e.text ?: e.contentDescription
                                    "${e.className?.toString()?.substringAfterLast(
                                        '.'
                                    )} \"${label ?: ""}\" ${e.boundsInScreen}"
                                }.orEmpty(),
                                message = result.getMessage(Locale.forLanguageTag("pt-BR")).toString()
                            )
                        }
                    page++
                } while (scrollOnePage())
            }
        }
        return found
    }

    /** Avança animações finitas e dá tempo ao Compose de publicar a árvore de acessibilidade atualizada. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        Thread.sleep(PUBLISH_MS)
    }

    /** Rola o primeiro rolável vertical em ~uma tela; `false` se não há rolável ou já está no fim. */
    private fun scrollOnePage(): Boolean {
        val nodes = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        val index = nodes.indexOfFirst { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
        if (index < 0) return false
        val node = nodes[index]
        val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
        val remaining = range.maxValue() - range.value()
        if (remaining <= 1f) return false
        val step = minOf(remaining, node.boundsInRoot.height * PAGE_FRACTION)
        compose.onAllNodes(hasScrollAction())[index].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
        settle()
        return true
    }

    companion object {
        private const val TAG = "AccessibilityAudit"
        private const val CONTROL = "Controle"
        private const val SETTLE_MS = 2_000L
        private const val PUBLISH_MS = 400L

        // Sobreposição entre páginas, para nenhum elemento ficar só cortado na borda
        private const val PAGE_FRACTION = 0.8f
        private val CHECKS = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(
            AccessibilityCheckPreset.LATEST
        )
        private val findings = mutableListOf<Finding>()

        // Uma linha por tela checada: elementos avaliados pelo ATF, quantos têm texto ou descrição e quantos títulos
        private val coverage = mutableListOf<String>()

        @JvmStatic
        @AfterClass
        fun writeReport() {
            val header = "tela,estado,variante,pagina,tipo,checagem,elemento,mensagem"
            val rows = findings.map {
                listOf(it.screen, it.state, it.variant, it.page, it.type, it.check, it.element, it.message)
                    .joinToString(",") { field -> "\"${field.toString().replace("\"", "\"\"")}\"" }
            }
            val csv = (listOf(header) + rows).joinToString("\n", postfix = "\n")
            Log.i(TAG, "${findings.size} resultados (ERROR e WARNING)")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dir = File(context.getExternalFilesDir(null), "evaluation").apply { mkdirs() }
            File(dir, "acessibilidade.csv").writeText(csv)
            val coverageCsv = (listOf("tela,estado,variante,pagina,elementos,com_rotulo,titulos") + coverage)
                .joinToString("\n", postfix = "\n")
            File(dir, "acessibilidade-cobertura.csv").writeText(coverageCsv)
        }
    }
}
