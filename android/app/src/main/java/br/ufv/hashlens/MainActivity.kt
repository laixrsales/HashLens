package br.ufv.hashlens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.ufv.hashlens.data.local.OnboardingPreferences
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.ui.navigation.HashLensNavHost
import br.ufv.hashlens.ui.navigation.startRoute
import br.ufv.hashlens.ui.theme.HashLensTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Única activity do app. Não recebe imagens de outros apps: a verificação começa só pelo Photo
 * Picker, por isso o manifesto não declara `ACTION_SEND` nem `ACTION_VIEW` de imagens (research
 * R16, FR-019). O único `ACTION_VIEW` é a volta do app da carteira (`hashlens-wc://`).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var walletSession: WalletSession

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleWalletReturn(intent)
        // "Como funciona" no primeiro uso (FR-037); lido uma vez, antes de montar a navegação
        val start = startRoute(onboardingPreferences)
        setContent {
            HashLensTheme {
                HashLensNavHost(startRoute = start)
            }
        }
    }

    // singleTask: a volta da carteira chega aqui, sem abrir outra activity
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWalletReturn(intent)
    }

    private fun handleWalletReturn(intent: Intent?) {
        intent?.dataString?.let(walletSession::handleDeepLink)
    }
}
