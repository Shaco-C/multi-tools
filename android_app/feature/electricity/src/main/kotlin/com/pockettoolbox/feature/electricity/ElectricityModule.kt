package com.pockettoolbox.feature.electricity

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pockettoolbox.core.backup.BackupContributor
import com.pockettoolbox.core.navigation.ToolEntry
import com.pockettoolbox.core.navigation.ToolModule
import com.pockettoolbox.feature.electricity.presentation.ElectricitySection
import com.pockettoolbox.feature.electricity.presentation.calculator.ElectricityCalculatorRoute
import com.pockettoolbox.feature.electricity.domain.ElectricityBillRepository
import com.pockettoolbox.feature.electricity.domain.ElectricityCsvExporter
import com.pockettoolbox.feature.electricity.presentation.history.ElectricityHistoryRoute
import com.pockettoolbox.feature.electricity.presentation.detail.ElectricityBillDetailRoute
import com.pockettoolbox.feature.electricity.presentation.trend.ElectricityTrendRoute
import com.pockettoolbox.feature.electricity.presentation.backup.ElectricityBackupRoute

object ElectricityRoutes {
    const val BillIdArgument = "billId"
    const val Calculator = "tools/electricity/calculator"
    const val History = "tools/electricity/history"
    const val Trend = "tools/electricity/trend"
    const val Backup = "tools/electricity/backup"
    const val Detail = "tools/electricity/history/{$BillIdArgument}"
    const val Edit = "tools/electricity/history/{$BillIdArgument}/edit"

    fun detail(billId: Long) = "tools/electricity/history/$billId"
    fun edit(billId: Long) = "tools/electricity/history/$billId/edit"
}

class ElectricityModule(
    private val repository: ElectricityBillRepository,
    private val backupContributor: BackupContributor,
    private val csvExporter: ElectricityCsvExporter,
) : ToolModule {
    override val entry = ToolEntry(
        id = "electricity",
        title = "电费分摊",
        description = "按每户实际用量，快速算清本月电费。",
        route = ElectricityRoutes.Calculator,
        statusLabel = "可开始计算",
        icon = {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
            )
        },
    )

    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        navController: NavHostController,
        onExitTool: () -> Unit,
    ) {
        navGraphBuilder.composable(ElectricityRoutes.Calculator) {
            ElectricityCalculatorRoute(
                repository = repository,
                onBack = onExitTool,
                onNavigate = { section -> navController.navigateToSection(section) },
            )
        }
        navGraphBuilder.composable(ElectricityRoutes.History) {
            ElectricityHistoryRoute(
                repository = repository,
                onBack = {
                    navController.popBackStack(ElectricityRoutes.Calculator, inclusive = false)
                },
                onNavigate = { section -> navController.navigateToSection(section) },
                onOpenBill = { billId -> navController.navigate(ElectricityRoutes.detail(billId)) },
                onOpenBackup = { navController.navigate(ElectricityRoutes.Backup) },
            )
        }
        navGraphBuilder.composable(
            route = ElectricityRoutes.Detail,
            arguments = listOf(navArgument(ElectricityRoutes.BillIdArgument) { type = NavType.LongType }),
        ) { entry ->
            val billId = entry.arguments?.getLong(ElectricityRoutes.BillIdArgument)
                ?: return@composable
            ElectricityBillDetailRoute(
                billId = billId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onNavigate = { section -> navController.navigateToSection(section) },
                onEdit = { id -> navController.navigate(ElectricityRoutes.edit(id)) },
                onDeleted = {
                    navController.popBackStack(ElectricityRoutes.History, inclusive = false)
                },
            )
        }
        navGraphBuilder.composable(
            route = ElectricityRoutes.Edit,
            arguments = listOf(navArgument(ElectricityRoutes.BillIdArgument) { type = NavType.LongType }),
        ) { entry ->
            val billId = entry.arguments?.getLong(ElectricityRoutes.BillIdArgument)
                ?: return@composable
            ElectricityCalculatorRoute(
                repository = repository,
                editingBillId = billId,
                onBack = { navController.popBackStack() },
                onNavigate = {},
            )
        }
        navGraphBuilder.composable(ElectricityRoutes.Trend) {
            ElectricityTrendRoute(
                repository = repository,
                onBack = {
                    navController.popBackStack(ElectricityRoutes.Calculator, inclusive = false)
                },
                onNavigate = { section -> navController.navigateToSection(section) },
            )
        }
        navGraphBuilder.composable(ElectricityRoutes.Backup) {
            ElectricityBackupRoute(
                contributor = backupContributor,
                csvExporter = csvExporter,
                onBack = { navController.popBackStack() },
            )
        }
    }

    private val ElectricitySection.route: String
        get() = when (this) {
            ElectricitySection.Calculator -> ElectricityRoutes.Calculator
            ElectricitySection.History -> ElectricityRoutes.History
            ElectricitySection.Trend -> ElectricityRoutes.Trend
        }

    private fun NavHostController.navigateToSection(section: ElectricitySection) {
        val targetRoute = section.route
        if (currentDestination?.route == targetRoute) return

        navigate(targetRoute) {
            popUpTo(ElectricityRoutes.Calculator) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }
}
