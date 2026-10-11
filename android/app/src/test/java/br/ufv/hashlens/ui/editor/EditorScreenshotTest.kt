package br.ufv.hashlens.ui.editor

import br.ufv.hashlens.ui.ScreenshotTest
import org.junit.Test

/**
 * T072: Editor em todos os estados de ux.md §4.6 (carregando, editando, sem alterações,
 * processando em resolução cheia, não elegível, aviso de permanência), mais ajuste de parâmetro,
 * recorte, antes/depois e imagem de terceiro.
 */
class EditorScreenshotTest : ScreenshotTest() {
    private fun editor(name: String, state: EditorUiState) = screenSnapshot("Editor_$name") {
        EditorScreen(state, onAction = {})
    }

    @Test
    fun loading() = editor("carregando", EditorSamples.loading)

    @Test
    fun editing() = editor("editando", EditorSamples.editing)

    @Test
    fun noChanges() = editor("sem_alteracoes", EditorSamples.noChanges)

    @Test
    fun adjustingBrightness() = editor("ajuste_brilho", EditorSamples.adjustingBrightness)

    @Test
    fun cropping() = editor("recorte", EditorSamples.cropping)

    @Test
    fun comparing() = editor("antes_depois", EditorSamples.comparing)

    @Test
    fun thirdParty() = editor("imagem_de_terceiro", EditorSamples.thirdParty)

    @Test
    fun confirming() = editor("aviso_permanencia", EditorSamples.confirming)

    @Test
    fun processing() = editor("processando", EditorSamples.processing)

    @Test
    fun pending() = editor("nao_elegivel_pendente", EditorSamples.pending)

    @Test
    fun registrationFailed() = editor("nao_elegivel_registro_falhou", EditorSamples.registrationFailed)

    @Test
    fun notRegistered() = editor("nao_elegivel_sem_registro", EditorSamples.notRegistered)

    @Test
    fun tampered() = editor("nao_elegivel_adulterado", EditorSamples.tampered)

    @Test
    fun importFailed() = editor("importacao_sem_conexao", EditorSamples.importFailed)

    @Test
    fun unavailable() = editor("nao_elegivel_arquivo", EditorSamples.unavailable)
}
