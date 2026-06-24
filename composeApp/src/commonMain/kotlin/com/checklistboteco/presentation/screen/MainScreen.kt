package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.User
import com.checklistboteco.presentation.viewmodel.ActivitiesManagementViewModel
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel
import com.checklistboteco.presentation.viewmodel.DashboardViewModel
import com.checklistboteco.presentation.viewmodel.PermissionManagementViewModel
import com.checklistboteco.presentation.viewmodel.WorkClockViewModel
import com.checklistboteco.presentation.viewmodel.InventoryCountViewModel

sealed class Tab(val title: String, val icon: ImageVector) {
    data object Checklist : Tab("Checklist", Icons.AutoMirrored.Filled.Assignment)
    data object WorkClock : Tab("Ponto", Icons.Default.AccessTime)
    data object Dashboard : Tab("Dashboard", Icons.Default.Dashboard)
    data object Activities : Tab("Atividades", Icons.Default.Settings)
    data object Permissions : Tab("Permissões", Icons.Default.AdminPanelSettings)
    data object Inventory : Tab("Contagem", Icons.Default.Inventory2)
}

@Composable
fun MainScreen(
    user: User,
    repository: ChecklistRepository,
    backendApiClient: BackendApiClient?,
    authToken: String?,
    remoteUserId: String?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    
    val tabs = remember(user) {
        buildList {
            add(Tab.Checklist)
            if (user.canUseWorkClock()) add(Tab.WorkClock)
            if (user.canUseInventoryModule()) add(Tab.Inventory)
            if (user.canUseDashboardModule()) add(Tab.Dashboard)
            if (user.canUseActivitiesModule()) add(Tab.Activities)
            if (user.canManagePermissions()) add(Tab.Permissions)
        }
    }

    LaunchedEffect(tabs.size) {
        if (selectedTabIndex >= tabs.size) {
            selectedTabIndex = 0
        }
    }

    Scaffold(
        bottomBar = {
            if (tabs.size > 1) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tabs[selectedTabIndex]) {
                is Tab.Checklist -> {
                    val viewModel = remember(user) { ChecklistViewModel(repository, user, scope) }
                    ChecklistScreen(
                        viewModel = viewModel,
                        user = user,
                        onLogout = onLogout
                    )
                }
                is Tab.Dashboard -> {
                    val viewModel = remember { DashboardViewModel(repository, scope) }
                    DashboardScreen(
                        viewModel = viewModel,
                        onBack = { selectedTabIndex = 0 }
                    )
                }
                is Tab.Activities -> {
                    val viewModel = remember { ActivitiesManagementViewModel(repository, scope) }
                    ActivitiesManagementScreen(
                        viewModel = viewModel,
                        onBack = { selectedTabIndex = 0 }
                    )
                }
                is Tab.WorkClock -> {
                    val viewModel = remember(user, authToken, remoteUserId) {
                        WorkClockViewModel(
                            repository = repository,
                            user = user,
                            scope = scope,
                            backendApiClient = backendApiClient,
                            authToken = authToken,
                            remoteUserId = remoteUserId
                        )
                    }
                    WorkClockScreen(
                        viewModel = viewModel,
                        user = user,
                        onBack = { selectedTabIndex = 0 }
                    )
                }
                is Tab.Permissions -> {
                    val viewModel = remember(user, authToken) {
                        PermissionManagementViewModel(
                            repository = repository,
                            currentUser = user,
                            backendApiClient = backendApiClient,
                            authToken = authToken,
                            scope = scope
                        )
                    }
                    PermissionManagementScreen(
                        viewModel = viewModel,
                        onBack = { selectedTabIndex = 0 }
                    )
                }
                is Tab.Inventory -> InventoryCountScreen(
                    viewModel=remember(user,authToken){ InventoryCountViewModel(repository,backendApiClient,authToken,scope) },
                    canCreate=user.canCreateInventoryCounts(),
                    canViewInsights=user.canViewInventoryInsights(),
                    canManageAdministrativeStock=user.canManageAdministrativeStock(),
                    isAdmin=user.canManagePermissions()
                )
            }
        }
    }
}
