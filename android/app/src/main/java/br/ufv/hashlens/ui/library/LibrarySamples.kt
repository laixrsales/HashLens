package br.ufv.hashlens.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.preview.SampleData.Photo
import br.ufv.hashlens.ui.theme.HashLensTheme
import java.time.Instant

/** Estados de Meu acervo (ux.md §4.10), compartilhados por previews e screenshot tests. */
object LibrarySamples {
    private fun at(iso: String): Instant = Instant.parse(iso)

    // Original registrada e duas versões dela: uma registrada, uma que estourou o prazo
    private val original =
        LibraryItem(
            1,
            ImageStatus.Original,
            at("2026-10-02T12:05:00Z"),
            true,
            recordId = 10,
            thumbnail = Photo.landscape()
        )
    private val edited =
        LibraryItem(
            2,
            ImageStatus.Edited,
            at("2026-10-04T17:32:00Z"),
            true,
            recordId = 11,
            thumbnail = Photo.grayscale()
        )
    private val editFailed =
        LibraryItem(4, ImageStatus.Failed, at("2026-10-09T15:20:00Z"), true, thumbnail = Photo.grayscale())

    // Versão de uma foto de outra pessoa, registrando
    private val importedPending =
        LibraryItem(5, ImageStatus.Pending, at("2026-10-08T11:00:00Z"), true, thumbnail = Photo.landscape())

    // Captura que nunca chegou à rede (carteira recusada)
    private val captureFailed =
        LibraryItem(3, ImageStatus.Failed, at("2026-10-07T20:45:00Z"), true, thumbnail = Photo.landscape())

    private val ownGroup = LibraryGroup("10", original.savedAt, listOf(original, edited, editFailed))
    private val importedGroup = LibraryGroup("50", originalTakenAt = null, items = listOf(importedPending))
    private val captureGroup = LibraryGroup("local-3", captureFailed.savedAt, listOf(captureFailed))

    val loading = LibraryUiState(loading = true)
    val empty = LibraryUiState(loading = false)

    val withItems = LibraryUiState(
        groups = listOf(ownGroup, importedGroup, captureGroup),
        loading = false,
        pendingCount = 1,
        failedCount = 2
    )

    val pending = withItems.copy(filter = LibraryFilter.Pending, groups = listOf(importedGroup))

    /** Com falha, uma delas já com o "Tentar de novo" em andamento. */
    val failed = withItems.copy(
        filter = LibraryFilter.Failed,
        groups = listOf(
            ownGroup.copy(items = listOf(editFailed.copy(retrying = true))),
            captureGroup
        )
    )

    val failedEmpty = withItems.copy(filter = LibraryFilter.Failed, groups = emptyList(), failedCount = 0)

    /** A original saiu da galeria: o registro continua, sem miniatura. */
    val fileRemoved = withItems.copy(
        groups = listOf(
            ownGroup.copy(items = listOf(original.copy(fileAvailable = false, thumbnail = null), edited, editFailed)),
            importedGroup,
            captureGroup
        )
    )

    // Folha de opções de cada tipo de foto
    val sheetConfirmed: LibraryItem = edited
    val sheetPending: LibraryItem = importedPending
    val sheetFailed: LibraryItem = captureFailed
    val sheetFileRemoved: LibraryItem = original.copy(fileAvailable = false, thumbnail = null)
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun LibraryWithItemsPreview() = LibraryPreview(LibrarySamples.withItems)

@PreviewLightDark
@Composable
private fun LibraryEmptyPreview() = LibraryPreview(LibrarySamples.empty)

@Preview
@Composable
private fun LibraryLoadingPreview() = LibraryPreview(LibrarySamples.loading)

@Preview
@Composable
private fun LibraryPendingPreview() = LibraryPreview(LibrarySamples.pending)

@PreviewLightDark
@Composable
private fun LibraryFailedPreview() = LibraryPreview(LibrarySamples.failed)

@Preview
@Composable
private fun LibraryFailedEmptyPreview() = LibraryPreview(LibrarySamples.failedEmpty)

@Preview
@Composable
private fun LibraryFileRemovedPreview() = LibraryPreview(LibrarySamples.fileRemoved)

@PreviewLightDark
@PreviewFontScale
@Composable
private fun LibrarySheetConfirmedPreview() = HashLensTheme {
    LibraryItemSheetFrame(LibrarySamples.sheetConfirmed, SampleData.ZONE)
}

@Preview
@Composable
private fun LibrarySheetFailedPreview() = HashLensTheme {
    LibraryItemSheetFrame(LibrarySamples.sheetFailed, SampleData.ZONE)
}

@Composable
private fun LibraryPreview(state: LibraryUiState) = HashLensTheme {
    LibraryScreen(state, onAction = {}, zone = SampleData.ZONE)
}
