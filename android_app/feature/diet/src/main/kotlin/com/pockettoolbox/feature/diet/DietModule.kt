package com.pockettoolbox.feature.diet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pockettoolbox.core.navigation.ToolEntry
import com.pockettoolbox.core.navigation.ToolModule
import com.pockettoolbox.feature.diet.domain.DietRepository
import com.pockettoolbox.feature.diet.presentation.DietRoute

class DietModule(private val repository: DietRepository) : ToolModule {
    override val entry = ToolEntry(
        id = "diet", title = "饮食不适记录", description = "记住不舒服的那一餐，给下次点餐留个提醒。",
        route = "tools/diet", statusLabel = "只记不适饮食",
        icon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
    )
    override fun registerRoutes(navGraphBuilder: NavGraphBuilder, navController: NavHostController, onExitTool: () -> Unit) {
        navGraphBuilder.composable(entry.route) { DietRoute(repository, onExitTool) }
    }
}
