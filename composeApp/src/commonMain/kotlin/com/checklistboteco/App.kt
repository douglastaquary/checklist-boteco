package com.checklistboteco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.checklistboteco.data.database.DatabaseDriverFactory
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.database.ChecklistDatabase
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.presentation.navigation.Screen
import com.checklistboteco.presentation.screen.ActivitiesManagementScreen
import com.checklistboteco.presentation.screen.AdminScreen
import com.checklistboteco.presentation.screen.UserManagementScreen
import com.checklistboteco.presentation.screen.ChecklistScreen
import com.checklistboteco.presentation.screen.LoginScreen
import com.checklistboteco.presentation.theme.ChecklistBotecoTheme
import com.checklistboteco.presentation.viewmodel.ActivitiesManagementViewModel
import com.checklistboteco.presentation.viewmodel.AdminViewModel
import com.checklistboteco.presentation.viewmodel.UserManagementViewModel
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel
import com.checklistboteco.presentation.viewmodel.LoginViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

        val currentScreen = remember { MutableStateFlow<Screen>(Screen.Login) }
        val screen by currentScreen.asStateFlow().collectAsState()

        when (val s = screen) {
            is Screen.Login -> {
                val loginViewModel = remember {
                    LoginViewModel(repository)
                }
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = { user -> currentScreen.value = Screen.Checklist(user) }
                )
            }

            is Screen.Checklist -> {
                val checklistViewModel = remember(s.user) {
                    ChecklistViewModel(repository, s.user, scope)
                }
                ChecklistScreen(
                    viewModel = checklistViewModel,
                    user = s.user,
                    onLogout = { currentScreen.value = Screen.Login },
                    onNavigateToAdmin = {
                        currentScreen.value = Screen.Admin(s.user)
                    },
                    onNavigateToActivitiesManagement = {
                        currentScreen.value = Screen.ActivitiesManagement(s.user)
                    },
                    onNavigateToUserManagement = {
                        currentScreen.value = Screen.UserManagement(s.user)
                    }
                )
            }

            is Screen.Admin -> {
                val adminViewModel = remember {
                    AdminViewModel(repository, scope)
                }
                AdminScreen(
                    viewModel = adminViewModel,
                    onBack = { currentScreen.value = Screen.Checklist(s.user) }
                )
            }

            is Screen.ActivitiesManagement -> {
                val activitiesViewModel = remember {
                    ActivitiesManagementViewModel(repository, scope)
                }
                ActivitiesManagementScreen(
                    viewModel = activitiesViewModel,
                    onBack = { currentScreen.value = Screen.Checklist(s.user) }
                )
            }

            is Screen.UserManagement -> {
                val userManagementViewModel = remember {
                    UserManagementViewModel(repository, scope)
                }
                UserManagementScreen(
                    viewModel = userManagementViewModel,
                    onBack = { currentScreen.value = Screen.Checklist(s.user) }
                )
            }
        }
    }
}
