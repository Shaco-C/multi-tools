package com.pockettoolbox.feature.fitness

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pockettoolbox.core.navigation.ToolEntry
import com.pockettoolbox.core.navigation.ToolModule
import com.pockettoolbox.feature.fitness.presentation.FitnessRoute

class FitnessModule(private val dependencies: FitnessFeatureDependencies) : ToolModule {
    override val entry = ToolEntry("fitness", "居家健身", "安排训练，做完一组记一下。", "tools/fitness",
        statusLabel = if (BuildConfig.FITNESS_RELEASE_CONTENT) null else "开发验证 · 内容待审",
        icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) })

    override fun registerRoutes(navGraphBuilder: NavGraphBuilder, navController: NavHostController, onExitTool: () -> Unit) {
        navGraphBuilder.composable(entry.route) { FitnessRoute(dependencies, onExitTool) }
    }
}
