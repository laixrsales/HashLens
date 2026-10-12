package br.ufv.hashlens.ui.library

import androidx.compose.ui.graphics.ImageBitmap
import br.ufv.hashlens.data.local.ImageOrigin
import br.ufv.hashlens.data.local.LocalImageEntity
import br.ufv.hashlens.data.local.RegistrationStatus
import br.ufv.hashlens.ui.components.ImageStatus
import java.time.Instant

// Acervo local → grupos de Meu acervo (ux.md §4.10)

/**
 * Agrupa pela original (`originalRecordId`; uma captura que ainda não chegou à rede forma grupo
 * próprio), com a original primeiro e as versões por data, e os grupos pela foto mais recente. O
 * [filter] mantém só as fotos com aquele status; os títulos dos grupos continuam os do acervo inteiro.
 */
internal fun List<LocalImageEntity>.toLibrary(
    filter: LibraryFilter,
    retrying: Set<Long>,
    thumbnails: Map<Long, ImageBitmap>
): LibraryUiState {
    val groups = groupBy { it.originalRecordId?.toString() ?: "local-${it.localId}" }
        .map { (key, images) ->
            val ordered = images.sortedWith(compareBy({ it.origin != ImageOrigin.CAPTURE }, { it.createdAt }))
            val original = ordered.firstOrNull { it.origin == ImageOrigin.CAPTURE }
            Triple(key, original, ordered)
        }
        .sortedByDescending { (_, _, ordered) -> ordered.maxOf { it.createdAt } }
        .mapNotNull { (key, original, ordered) ->
            val items = ordered
                .map { it.toItem(it.localId in retrying, thumbnails[it.localId]) }
                .filter { filter.accepts(it.status) }
            items.takeIf { it.isNotEmpty() }?.let {
                LibraryGroup(key, original?.createdAt?.let(Instant::ofEpochMilli), it)
            }
        }
    val statuses = map { it.libraryStatus }
    return LibraryUiState(
        groups = groups,
        filter = filter,
        loading = false,
        pendingCount = statuses.count { it == ImageStatus.Pending },
        failedCount = statuses.count { it == ImageStatus.Failed }
    )
}

private fun LibraryFilter.accepts(status: ImageStatus): Boolean = when (this) {
    LibraryFilter.All -> true
    LibraryFilter.Pending -> status == ImageStatus.Pending
    LibraryFilter.Failed -> status == ImageStatus.Failed
}

private fun LocalImageEntity.toItem(retrying: Boolean, thumbnail: ImageBitmap?) = LibraryItem(
    localId = localId,
    status = libraryStatus,
    savedAt = Instant.ofEpochMilli(createdAt),
    fileAvailable = fileAvailable,
    recordId = recordId.takeIf { status == RegistrationStatus.CONFIRMED },
    thumbnail = thumbnail.takeIf { fileAvailable },
    retrying = retrying
)

/**
 * Selo do vocabulário de status (ux.md §5). `LOCAL_ONLY` só fica parada se o app fechou entre salvar
 * e enviar: nada foi enviado, então é uma falha que se resolve com "Tentar de novo".
 */
internal val LocalImageEntity.libraryStatus: ImageStatus
    get() = when (status) {
        RegistrationStatus.CONFIRMED -> if (origin == ImageOrigin.CAPTURE) ImageStatus.Original else ImageStatus.Edited
        RegistrationStatus.AWAITING_WALLET, RegistrationStatus.PENDING -> ImageStatus.Pending
        RegistrationStatus.FAILED, RegistrationStatus.LOCAL_ONLY -> ImageStatus.Failed
    }
