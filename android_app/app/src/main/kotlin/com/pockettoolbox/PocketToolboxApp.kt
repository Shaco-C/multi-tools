package com.pockettoolbox.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pockettoolbox.core.navigation.ToolModule
import com.pockettoolbox.feature.electricity.ElectricityModule

private const val HomeRoute = "home"

@Composable
fun PocketToolboxApp() {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as PocketToolboxApplication
    val modules: List<ToolModule> = remember(application) {
        listOf(
            ElectricityModule(
                repository = application.electricityDependencies.repository,
                backupContributor = application.electricityDependencies.backupContributor,
                csvExporter = application.electricityDependencies.csvExporter,
            ),
        )
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable(HomeRoute) {
            ToolboxHomeScreen(
                tools = modules.map { it.entry },
                onOpenTool = { route -> navController.navigate(route) },
            )
        }
        modules.forEach { module ->
            module.registerRoutes(
                navGraphBuilder = this,
                navController = navController,
                onExitTool = {
                    navController.popBackStack(
                        route = HomeRoute,
                        inclusive = false,
                    )
                },
            )
        }
    }
}
