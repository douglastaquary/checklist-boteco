package com.checklistboteco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.checklistboteco.data.database.DatabaseDriverFactory
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.database.ChecklistDatabase
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.presentation.navigation.Screen
import com.checklistboteco.presentation.screen.ActivitiesManagementScreen
import com.checklistboteco.presentation.screen.AdminScreen
import com.checklistboteco.presentation.screen.UserManagementScreen
import com.checklistboteco.presentation.screen.ChecklistScreen
import com.checklistboteco.presentation.screen.LoginScreen
import com.checklistboteco.presentation.screen.DashboardScreen
import com.checklistboteco.presentation.theme.ChecklistBotecoTheme
import com.checklistboteco.presentation.viewmodel.ActivitiesManagementViewModel
import com.checklistboteco.presentation.viewmodel.AdminViewModel
import com.checklistboteco.presentation.viewmodel.UserManagementViewModel
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel
import com.checklistboteco.presentation.viewmodel.LoginViewModel
import com.checklistboteco.presentation.viewmodel.DashboardViewModel

@Composable
fun App(
    databaseDriverFactory: DatabaseDriverFactory
) {
    ChecklistBotecoTheme {
        val database = remember {
            ChecklistDatabase(databaseDriverFactory.createDriver())
        }
        val repository = remember {
            ChecklistRepository(database)
        }
        val scope = rememberCoroutineScope()

        // Saver customizado para persistir a tela e o usuário logado entre rotações
        val screenSaver = listSaver<Screen, Any>(
            save = { screen ->
                when (screen) {
                    is Screen.Login -> listOf("Login")
                    is Screen.Checklist -> listOf("Checklist", screen.user.id, screen.user.name, screen.user.password, screen.user.area.name, screen.user.permissionLevel.name, screen.user.allowedAreas.joinToString(",") { it.name })
                    is Screen.Dashboard -> listOf("Dashboard", screen.user.id, screen.user.name, screen.user.password, screen.user.area.name, screen.user.permissionLevel.name, screen.user.allowedAreas.joinToString(",") { it.name })
                    is Screen.Admin -> listOf("Admin", screen.user.id, screen.user.name, screen.user.password, screen.user.area.name, screen.user.permissionLevel.name, screen.user.allowedAreas.joinToString(",") { it.name })
                    is Screen.ActivitiesManagement -> listOf("ActivitiesManagement", screen.user.id, screen.user.name, screen.user.password, screen.user.area.name, screen.user.permissionLevel.name, screen.user.allowedAreas.joinToString(",") { it.name })
                    is Screen.UserManagement -> listOf("UserManagement", screen.user.id, screen.user.name, screen.user.password, screen.user.area.name, screen.user.permissionLevel.name, screen.user.allowedAreas.joinToString(",") { it.name })
                }
            },
            restore = { list ->
                val type = list[0] as String
                if (type == "Login") return@listSaver Screen.Login
                
                val user = User(
                    id = list[1] as Long,
                    name = list[2] as String,
                    password = list[3] as String,
                    area = Area.fromString(list[4] as String),
                    permissionLevel = PermissionLevel.fromString(list[5] as String),
                    allowedAreas = (list[6] as String).split(",").filter { it.isNotEmpty() }.map { Area.fromString(it) }
                )
                
                when (type) {
                    "Checklist" -> Screen.Checklist(user)
                    "Dashboard" -> Screen.Dashboard(user)
                    "Admin" -> Screen.Admin(user)
                    "ActivitiesManagement" -> Screen.ActivitiesManagement(user)
                    "UserManagement" -> Screen.UserManagement(user)
                    else -> Screen.Login
                }
            }
        )

        var currentScreen by rememberSaveable(stateSaver = screenSaver) { 
            mutableStateOf<Screen>(Screen.Login) 
        }

        when (val s = currentScreen) {
            is Screen.Login -> {
                val loginViewModel = remember { LoginViewModel(repository) }
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = { user -> currentScreen = Screen.Checklist(user) }
                )
            }

            is Screen.Checklist -> {
                val checklistViewModel = remember(s.user) {
                    ChecklistViewModel(repository, s.user, scope)
                }
                ChecklistScreen(
                    viewModel = checklistViewModel,
                    user = s.user,
                    onLogout = { currentScreen = Screen.Login },
                    onNavigateToAdmin = {
                        currentScreen = Screen.Dashboard(s.user)
                    },
                    onNavigateToActivitiesManagement = {
                        currentScreen = Screen.ActivitiesManagement(s.user)
                    },
                    onNavigateToUserManagement = {
                        currentScreen = Screen.UserManagement(s.user)
                    }
                )
            }

            is Screen.Dashboard -> {
                val dashboardViewModel = remember { DashboardViewModel(repository, scope) }
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onBack = { currentScreen = Screen.Checklist(s.user) }
                )
            }

            is Screen.Admin -> {
                val adminViewModel = remember { AdminViewModel(repository, scope) }
                AdminScreen(
                    viewModel = adminViewModel,
                    onBack = { currentScreen = Screen.Checklist(s.user) }
                )
            }

            is Screen.ActivitiesManagement -> {
                val activitiesViewModel = remember { ActivitiesManagementViewModel(repository, scope) }
                ActivitiesManagementScreen(
                    viewModel = activitiesViewModel,
                    onBack = { currentScreen = Screen.Checklist(s.user) }
                )
            }

            is Screen.UserManagement -> {
                val userManagementViewModel = remember { UserManagementViewModel(repository, scope) }
                UserManagementScreen(
                    viewModel = userManagementViewModel,
                    onBack = { currentScreen = Screen.Checklist(s.user) }
                )
            }
        }
    }
}
