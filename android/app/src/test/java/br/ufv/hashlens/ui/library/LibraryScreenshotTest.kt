package br.ufv.hashlens.ui.library

import br.ufv.hashlens.ui.ScreenshotTest
import br.ufv.hashlens.ui.preview.SampleData
import org.junit.Test

/**
 * T088: Meu acervo em todos os estados de ux.md §4.10 (vazio, com itens, pendentes, falhas com
 * "Tentar de novo", arquivo removido da galeria), mais carregando, filtro sem fotos e a folha de
 * opções de cada tipo de foto.
 */
class LibraryScreenshotTest : ScreenshotTest() {
    private fun library(name: String, state: LibraryUiState) = screenSnapshot("Acervo_$name") {
        LibraryScreen(state, onAction = {}, zone = SampleData.ZONE)
    }

    private fun sheet(name: String, item: LibraryItem) = screenSnapshot("Acervo_opcoes_$name") {
        LibraryItemSheetFrame(item, SampleData.ZONE)
    }

    @Test
    fun loading() = library("carregando", LibrarySamples.loading)

    @Test
    fun empty() = library("vazio", LibrarySamples.empty)

    @Test
    fun withItems() = library("com_itens", LibrarySamples.withItems)

    @Test
    fun pending() = library("pendentes", LibrarySamples.pending)

    @Test
    fun failed() = library("falhas", LibrarySamples.failed)

    @Test
    fun failedEmpty() = library("falhas_vazio", LibrarySamples.failedEmpty)

    @Test
    fun fileRemoved() = library("arquivo_removido", LibrarySamples.fileRemoved)

    @Test
    fun sheetConfirmed() = sheet("registrada", LibrarySamples.sheetConfirmed)

    @Test
    fun sheetPending() = sheet("registrando", LibrarySamples.sheetPending)

    @Test
    fun sheetFailed() = sheet("falhou", LibrarySamples.sheetFailed)

    @Test
    fun sheetFileRemoved() = sheet("arquivo_removido", LibrarySamples.sheetFileRemoved)
}
