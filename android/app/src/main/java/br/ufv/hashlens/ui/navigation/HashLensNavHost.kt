package br.ufv.hashlens.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.rememberBottomSheetNavigator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.ufv.hashlens.ui.capture.CaptureRoute
import br.ufv.hashlens.ui.home.HomeAction
import br.ufv.hashlens.ui.home.HomeRoute
import br.ufv.hashlens.ui.verify.VerifyRoute
import br.ufv.hashlens.ui.verify.VerifyViewModel
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit

/**
 * Navegação do app (ux.md §3). A lista de carteiras do AppKit é um bottom sheet de navegação
 * ([appKitGraph]), por isso o `NavHost` fica dentro de um [ModalBottomSheetLayout].
 */
@Composable
fun HashLensNavHost(modifier: Modifier = Modifier) {
    val sheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(sheetNavigator)
    val backStackEntry by navController.currentBackStackEntryAsState()

    ModalBottomSheetLayout(
        sheetNavigator,
        modifier = modifier,
        sheetBackgroundColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        NavHost(navController = navController, startDestination = Route.Home.path) {
            composable(Route.Home.path) {
                HomeRoute(
                    walletListOpen = backStackEntry?.destination?.route != Route.Home.path,
                    onOpenWalletList = { navController.openAppKit(shouldOpenChooseNetwork = false) },
                    onRegisterPhoto = { navController.navigate(Route.Capture.path) },
                    onNavigate = { action -> action.route()?.let { navController.navigate(it.path) } }
                )
            }
            composable(Route.Capture.path) {
                CaptureRoute(
                    onClose = { navController.popBackStack(Route.Home.path, inclusive = false) },
                    onViewRecord = { navController.navigate(Route.verificationResult(it)) }
                )
            }
            // Verificação pública (FR-019): sem guarda de sessão, sem carteira e sem custo. Só
            // "Registrar foto" passa pela carteira (WalletViewModel), antes de abrir a câmera.
            composable(Route.Verify.path) {
                VerifyRoute(
                    onClose = { navController.popBackStack() },
                    onOpenHistory = { navController.navigate(Route.Genealogy.path) }
                )
            }
            composable(
                Route.VerificationResult.path,
                arguments = listOf(navArgument(VerifyViewModel.RECORD_ID) { type = NavType.LongType })
            ) {
                VerifyRoute(
                    onClose = { navController.popBackStack() },
                    onOpenHistory = { navController.navigate(Route.Genealogy.path) }
                )
            }
            appKitGraph(navController)
            // Telas das próximas histórias (US3–US6): ainda vazias
            Route.entries.filter { it !in BUILT }.forEach { route ->
                composable(route.path) { Box(Modifier.fillMaxSize()) }
            }
        }
    }
}

/** Destinos que já têm tela; os demais ficam vazios até a sua história. */
private val BUILT = setOf(Route.Home, Route.Capture, Route.Verify, Route.VerificationResult)

/** Destino de cada ação do Início; `null` quando a ação passa antes pela carteira ou é uma folha. */
internal fun HomeAction.route(): Route? = when (this) {
    HomeAction.VerifyImage -> Route.Verify
    HomeAction.OpenLibrary -> Route.Library
    HomeAction.ImportForEdit -> Route.Editor
    HomeAction.HowItWorks -> Route.Onboarding
    HomeAction.RegisterPhoto, HomeAction.OpenWallet -> null
}
