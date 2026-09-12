package com.pockettoolbox.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

@Immutable
data class ToolEntry(
    val id: String,
    val title: String,
    val description: String,
    val route: String,
    val statusLabel: String? = null,
    val icon: @Composable () -> Unit,
)

/**
 * 工具箱的功能扩展点。新增工具时实现此接口并在应用组合根注册，
 * 工具自己的页面、数据库和业务逻辑不需要放进应用壳。
 */
interface ToolModule {
    val entry: ToolEntry

    fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
        onExitTool: () -> Unit,
    )
}
