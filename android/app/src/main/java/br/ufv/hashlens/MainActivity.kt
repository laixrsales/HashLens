package br.ufv.hashlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.ufv.hashlens.ui.navigation.HashLensNavHost
import br.ufv.hashlens.ui.theme.HashLensTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única activity do app. Não recebe imagens de outros apps: a verificação começa só pelo Photo
 * Picker, por isso o manifesto não declara `ACTION_SEND`/`ACTION_VIEW` (research R16, FR-019).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HashLensTheme {
                HashLensNavHost()
            }
        }
    }
}
