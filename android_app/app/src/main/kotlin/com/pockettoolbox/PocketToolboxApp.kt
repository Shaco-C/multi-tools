package com.pockettoolbox.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pockettoolbox.core.designsystem.ToolboxPalette
import com.pockettoolbox.core.navigation.ToolModule
import com.pockettoolbox.feature.electricity.ElectricityModule
import com.pockettoolbox.feature.diet.DietModule
import com.pockettoolbox.feature.fitness.FitnessFeatureDependencies
import com.pockettoolbox.feature.fitness.FitnessModule

private const val HomeRoute = "home"

@Composable
fun PocketToolboxApp(
    palette: ToolboxPalette,
    onPaletteChange: (ToolboxPalette) -> Unit,
) {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as PocketToolboxApplication
    val modules: List<ToolModule> = remember(application) {
        buildList {
            add(
                ElectricityModule(
                    repository = application.electricityDependencies.repository,
                    backupContributor = application.electricityDependencies.backupContributor,
                    csvExporter = application.electricityDependencies.csvExporter,
                ),
            )
            add(DietModule(application.dietDependencies.repository))
            if (FitnessFeatureDependencies.isEnabled) add(FitnessModule(application.fitnessDependencies))
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable(HomeRoute) {
            ToolboxHomeScreen(
                tools = modules.map { it.entry },
                onOpenTool = { route -> navController.navigate(route) },
                palette = palette,
                onPaletteChange = onPaletteChange,
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
