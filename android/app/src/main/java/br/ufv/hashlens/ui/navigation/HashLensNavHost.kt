package br.ufv.hashlens.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.rememberBottomSheetNavigator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.ufv.hashlens.ui.capture.CaptureRoute
import br.ufv.hashlens.ui.editor.EditorRoute
import br.ufv.hashlens.ui.editor.EditorViewModel
import br.ufv.hashlens.ui.genealogy.GenealogyRoute
import br.ufv.hashlens.ui.genealogy.GenealogyViewModel
import br.ufv.hashlens.ui.home.HomeAction
import br.ufv.hashlens.ui.home.HomeRoute
import br.ufv.hashlens.ui.library.LibraryRoute
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
                    onOpenHistory = { navController.navigate(Route.genealogy(it)) }
                )
            }
            composable(
                Route.VerificationResult.path,
                arguments = listOf(navArgument(VerifyViewModel.RECORD_ID) { type = NavType.LongType })
            ) {
                VerifyRoute(
                    onClose = { navController.popBackStack() },
                    onOpenHistory = { navController.navigate(Route.genealogy(it)) }
                )
            }
            libraryDestinations(navController) { backStackEntry?.destination?.route != Route.Library.path }
            editorDestinations(navController)
            appKitGraph(navController)
            // Telas das próximas histórias (US6): ainda vazias
            Route.entries.filter { it !in BUILT }.forEach { route ->
                composable(route.path) { Box(Modifier.fillMaxSize()) }
            }
        }
    }
}

/**
 * Histórico da imagem (US5) e Meu acervo (US6).
 *
 * @param walletListOpen a lista de carteiras do AppKit está por cima do acervo.
 */
private fun NavGraphBuilder.libraryDestinations(navController: NavHostController, walletListOpen: () -> Boolean) {
    // Pública como a verificação: só leituras do contrato. Cada versão da árvore abre o próprio
    // Histórico por cima, e "voltar" retorna à anterior.
    composable(
        Route.Genealogy.path,
        arguments = listOf(navArgument(GenealogyViewModel.RECORD_ID) { type = NavType.LongType })
    ) {
        GenealogyRoute(
            onClose = { navController.popBackStack() },
            onOpenRecord = { navController.navigate(Route.genealogy(it)) }
        )
    }
    // Meu acervo (US6): histórico e edição das fotos registradas; "Registrar foto" passa pela carteira
    composable(Route.Library.path) {
        LibraryRoute(
            walletListOpen = walletListOpen(),
            onOpenWalletList = { navController.openAppKit(shouldOpenChooseNetwork = false) },
            onClose = { navController.popBackStack() },
            onRegisterPhoto = { navController.navigate(Route.Capture.path) },
            onOpenHistory = { navController.navigate(Route.genealogy(it)) },
            onEdit = { navController.navigate(Route.editor(it)) }
        )
    }
}

/** Editor aberto pelo acervo (US6), com o localId da imagem, ou pela importação (US4), sem ele. */
private fun NavGraphBuilder.editorDestinations(navController: NavHostController) {
    composable(Route.ImportForEdit.path) {
        EditorRoute(
            onClose = { navController.popBackStack() },
            onViewRecord = { navController.navigate(Route.verificationResult(it)) }
        )
    }
    composable(
        Route.Editor.path,
        arguments = listOf(navArgument(EditorViewModel.PARENT_LOCAL_ID) { type = NavType.LongType })
    ) {
        EditorRoute(
            onClose = { navController.popBackStack() },
            onViewRecord = { navController.navigate(Route.verificationResult(it)) }
        )
    }
}

/** Destinos que já têm tela; os demais ficam vazios até a sua história. */
private val BUILT = setOf(
    Route.Home,
    Route.Capture,
    Route.Verify,
    Route.VerificationResult,
    Route.Editor,
    Route.ImportForEdit,
    Route.Genealogy,
    Route.Library
)

/** Destino de cada ação do Início; `null` quando a ação passa antes pela carteira ou é uma folha. */
internal fun HomeAction.route(): Route? = when (this) {
    HomeAction.VerifyImage -> Route.Verify
    HomeAction.OpenLibrary -> Route.Library
    HomeAction.HowItWorks -> Route.Onboarding
    HomeAction.ImportForEdit -> Route.ImportForEdit
    HomeAction.RegisterPhoto, HomeAction.OpenWallet -> null
}
