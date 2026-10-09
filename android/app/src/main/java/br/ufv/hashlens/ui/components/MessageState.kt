package br.ufv.hashlens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import br.ufv.hashlens.ui.theme.HashLensTheme

/** Layout comum de [EmptyState] e [ErrorState]: ícone, título, frase e uma ação. */
@Composable
internal fun MessageState(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val spacing = HashLensTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // Decorativo: o título diz o que aconteceu
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(HashLensTheme.sizes.minTouchTarget)
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(spacing.lg))
            action()
        }
    }
}
