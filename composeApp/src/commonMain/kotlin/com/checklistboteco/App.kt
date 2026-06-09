package com.checklistboteco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.database.DatabaseDriverFactory
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.database.ChecklistDatabase
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.WorkSector
import com.checklistboteco.presentation.navigation.Screen
import com.checklistboteco.presentation.screen.LoginScreen
import com.checklistboteco.presentation.screen.MainScreen
import com.checklistboteco.presentation.screen.UserRegistrationScreen
import com.checklistboteco.presentation.theme.ChecklistBotecoTheme
import com.checklistboteco.presentation.viewmodel.LoginViewModel
import com.checklistboteco.presentation.viewmodel.UserRegistrationViewModel

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
        val backendApiClient = remember {
            BackendApiClient.fromEnvironment()
        }

        val screenSaver = listSaver<Screen, Any>(
            save = { screen ->
                when (screen) {
                    is Screen.Login -> listOf("Login")
                    is Screen.RegisterUser -> listOf("RegisterUser")
                    is Screen.Main -> listOf(
                        "Main",
                        screen.user.id,
                        screen.user.name,
                        screen.user.email,
                        screen.user.password,
                        screen.user.area.name,
                        screen.user.workSector.name,
                        screen.user.permissionLevel.name,
                        screen.user.allowedAreas.joinToString(",") { it.name },
                        screen.user.createdAt,
                        screen.user.featurePermissions.canRegisterUsers,
                        screen.user.featurePermissions.canCreateActivities,
                        screen.user.featurePermissions.canEditUsers,
                        screen.authToken.orEmpty(),
                        screen.remoteUserId.orEmpty()
                    )
                }
            },
            restore = { list ->
                val type = list[0] as String
                if (type == "Login") Screen.Login
                else if (type == "RegisterUser") Screen.RegisterUser
                else {
                    val user = User(
                        id = list[1] as Long,
                        name = list[2] as String,
                        email = list[3] as String,
                        password = list[4] as String,
                        area = Area.fromString(list[5] as String),
                        workSector = WorkSector.fromString(list[6] as String),
                        permissionLevel = PermissionLevel.fromString(list[7] as String),
                        allowedAreas = (list[8] as String).split(",")
                            .filter { it.isNotEmpty() }
                            .map { Area.fromString(it) },
                        createdAt = list[9] as Long,
                        featurePermissions = com.checklistboteco.domain.model.FeaturePermissions(
                            canRegisterUsers = list[10] as Boolean,
                            canCreateActivities = list[11] as Boolean,
                            canEditUsers = list[12] as Boolean
                        )
                    )
                    Screen.Main(
                        user = user,
                        authToken = (list.getOrNull(13) as? String)?.ifBlank { null },
                        remoteUserId = (list.getOrNull(14) as? String)?.ifBlank { null }
                    )
                }
            }
        )

        var currentScreen by rememberSaveable(stateSaver = screenSaver) { 
            mutableStateOf<Screen>(Screen.Login) 
        }

        when (val s = currentScreen) {
            is Screen.Login -> {
                val loginViewModel = remember { LoginViewModel(repository, backendApiClient) }
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = { user ->
                        val state = loginViewModel.uiState.value
                        currentScreen = Screen.Main(user, state.authToken, state.remoteUserId)
                    },
                    onNewUserClick = { currentScreen = Screen.RegisterUser }
                )
            }

            is Screen.RegisterUser -> {
                val userRegistrationViewModel = remember { UserRegistrationViewModel(repository) }
                UserRegistrationScreen(
                    viewModel = userRegistrationViewModel,
                    onBack = { currentScreen = Screen.Login }
                )
            }

            is Screen.Main -> {
                MainScreen(
                    user = s.user,
                    repository = repository,
                    backendApiClient = backendApiClient,
                    authToken = s.authToken,
                    remoteUserId = s.remoteUserId,
                    onLogout = { currentScreen = Screen.Login }
                )
            }
        }
    }
}
