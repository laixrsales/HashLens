package br.ufv.hashlens.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun HashLensNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Route.Home.path, modifier = modifier) {
        // Rotas vazias até as telas existirem (Fundação de UI e histórias US1–US6)
        Route.entries.forEach { route ->
            composable(route.path) { Box(Modifier.fillMaxSize()) }
        }
    }
}
