package br.ufv.hashlens.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** `true` com "Remover animações" ligado no sistema (escala de animação 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Duração de uma animação: [millis], ou 0 com movimento reduzido. */
@Composable
fun motionDuration(millis: Int): Int = if (rememberReducedMotion()) 0 else millis
