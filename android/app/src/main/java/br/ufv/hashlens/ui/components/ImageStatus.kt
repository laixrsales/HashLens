package br.ufv.hashlens.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import br.ufv.hashlens.R
import br.ufv.hashlens.ui.theme.HashLensTheme
import br.ufv.hashlens.ui.theme.StatusColor

/**
 * Vocabulário visual de status (ux.md §5): cada status tem sempre o mesmo ícone, palavra e cor,
 * e a cor nunca aparece sem o ícone e a palavra.
 */
enum class ImageStatus(val icon: ImageVector, @StringRes val title: Int) {
    Original(Icons.Outlined.Verified, R.string.status_original),
    Edited(Icons.Outlined.Difference, R.string.status_edited),
    VisualMatch(Icons.Outlined.ImageSearch, R.string.status_visual_match),
    Tampered(Icons.Outlined.GppBad, R.string.status_tampered),
    NotRegistered(Icons.AutoMirrored.Outlined.HelpOutline, R.string.status_not_registered),
    Pending(Icons.Outlined.Schedule, R.string.status_pending),
    Failed(Icons.Outlined.ErrorOutline, R.string.status_failed);

    val color: StatusColor
        @Composable @ReadOnlyComposable
        get() = with(HashLensTheme.statusColors) {
            when (this@ImageStatus) {
                Original -> original
                Edited -> edited
                VisualMatch -> visualMatch
                Tampered -> tampered
                NotRegistered -> notRegistered
                Pending -> pending
                Failed -> failed
            }
        }
}
