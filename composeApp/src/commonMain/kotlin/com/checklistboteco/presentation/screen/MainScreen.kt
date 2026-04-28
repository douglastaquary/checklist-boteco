package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.presentation.viewmodel.ActivitiesManagementViewModel
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel
import com.checklistboteco.presentation.viewmodel.DashboardViewModel
import com.checklistboteco.presentation.viewmodel.UserManagementViewModel

sealed class Tab(val title: String, val icon: ImageVector) {
    data object Checklist : Tab("Checklist", Icons.AutoMirrored.Filled.Assignment)
    data object Dashboard : Tab("Dashboard", Icons.Default.Dashboard)
    data object Activities : Tab("Atividades", Icons.Default.Settings)
    data object Users : Tab("Usuários", Icons.Default.People)
}

@Composable
fun MainScreen(
    user: User,
    repository: ChecklistRepository,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    
    val tabs = remember(user) {
        if (user.permissionLevel == PermissionLevel.ADMIN) {
            listOf(Tab.Checklist, Tab.Dashboard, Tab.Activities, Tab.Users)
        } else {
            listOf(Tab.Checklist)
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
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val currentTab = tabs[selectedTabIndex]) {
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
                is Tab.Users -> {
                    val viewModel = remember { UserManagementViewModel(repository, scope) }
                    UserManagementScreen(
                        viewModel = viewModel,
                        onBack = { selectedTabIndex = 0 }
                    )
                }
            }
        }
    }
}
