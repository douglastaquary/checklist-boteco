package com.checklistboteco.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.checklistboteco.domain.model.User

enum class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    Checklist(
        route = "checklist",
        title = "Checklist",
        icon = Icons.AutoMirrored.Filled.Assignment,
        contentDescription = "Checklist"
    ),
    WorkClock(
        route = "work_clock",
        title = "Ponto",
        icon = Icons.Default.AccessTime,
        contentDescription = "Ponto"
    ),
    Inventory(
        route = "inventory",
        title = "Contagem",
        icon = Icons.Default.Inventory2,
        contentDescription = "Contagem de estoque"
    ),
    Dashboard(
        route = "dashboard",
        title = "Dashboard",
        icon = Icons.Default.Dashboard,
        contentDescription = "Dashboard"
    ),
    Activities(
        route = "activities",
        title = "Atividades",
        icon = Icons.Default.Settings,
        contentDescription = "Gerenciar atividades"
    ),
    Permissions(
        route = "permissions",
        title = "Permissões",
        icon = Icons.Default.AdminPanelSettings,
        contentDescription = "Gerenciar permissões"
    );

    companion object {
        fun availableFor(user: User): List<AppDestination> = buildList {
            add(Checklist)
            if (user.canUseWorkClock()) add(WorkClock)
            if (user.canUseInventoryModule()) add(Inventory)
            if (user.canUseDashboardModule()) add(Dashboard)
            if (user.canUseActivitiesModule()) add(Activities)
            if (user.canManagePermissions()) add(Permissions)
        }

        fun layoutFor(user: User): AppNavigationLayout {
            val available = availableFor(user)
            return if (available.size <= 4) {
                AppNavigationLayout(primary = available, overflow = emptyList())
            } else {
                AppNavigationLayout(primary = available.take(3), overflow = available.drop(3))
            }
        }
    }
}

data class AppNavigationLayout(
    val primary: List<AppDestination>,
    val overflow: List<AppDestination>
)
