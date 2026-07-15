package com.checklistboteco

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.remote.SyncApiClient
import com.checklistboteco.data.database.DatabaseDriverFactory
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.data.sync.NoOpSyncScheduler
import com.checklistboteco.data.sync.SyncCoordinator
import com.checklistboteco.data.sync.SyncScheduler
import com.checklistboteco.database.ChecklistDatabase
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.WorkSector
import com.checklistboteco.presentation.navigation.Screen
import com.checklistboteco.presentation.screen.ChangePasswordScreen
import com.checklistboteco.presentation.screen.LoginScreen
import com.checklistboteco.presentation.screen.MainScreen
import com.checklistboteco.presentation.screen.UserRegistrationScreen
import com.checklistboteco.presentation.components.GlobalAppFeedback
import com.checklistboteco.presentation.theme.ChecklistBotecoTheme
import com.checklistboteco.presentation.viewmodel.LoginViewModel
import com.checklistboteco.platform.SessionExpiredNotifier
import com.checklistboteco.presentation.viewmodel.UserRegistrationViewModel

@Composable
fun App(
    databaseDriverFactory: DatabaseDriverFactory,
    syncScheduler: SyncScheduler = NoOpSyncScheduler
) {
    ChecklistBotecoTheme {
        Box(Modifier.fillMaxSize()) {
        val database = remember {
            ChecklistDatabase(databaseDriverFactory.createDriver())
        }
        val syncCoordinatorState = remember { mutableStateOf<SyncCoordinator?>(null) }
        val repository = remember {
            ChecklistRepository(database) {
                syncCoordinatorState.value?.requestSync()
            }
        }
        val backendApiClient = remember {
            BackendApiClient.fromEnvironment()
        }
        val syncApiClient = remember {
            SyncApiClient.fromEnvironment()
        }
        val syncCoordinator = remember(repository, syncApiClient, syncScheduler) {
            SyncCoordinator(
                repository = repository,
                syncApiClient = syncApiClient,
                scheduler = syncScheduler
            )
        }

        LaunchedEffect(syncCoordinator) {
            syncCoordinatorState.value = syncCoordinator
            syncCoordinator.start()
        }

        LaunchedEffect(backendApiClient) {
            if (backendApiClient == null) {
                repository.seedInitialData()
            } else {
                repository.purgeLocalSeedArtifactsIfNeeded()
            }
        }

        val screenSaver = listSaver<Screen, Any>(
            save = { screen ->
                when (screen) {
                    is Screen.Login -> listOf("Login")
                    is Screen.RegisterUser -> listOf("RegisterUser")
                    is Screen.ChangePassword -> listOf("ChangePassword")
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
                        screen.user.remoteId.orEmpty(),
                        screen.user.featurePermissions.canRegisterUsers,
                        screen.user.featurePermissions.canCreateActivities,
                        screen.user.featurePermissions.canEditUsers,
                        screen.user.featurePermissions.canCreateInventoryCounts,
                        screen.user.featurePermissions.canViewInventoryInsights,
                        screen.user.featurePermissions.canManageAdministrativeStock,
                        screen.authToken.orEmpty(),
                        screen.remoteUserId.orEmpty()
                    )
                }
            },
            restore = { list ->
                val type = list[0] as String
                if (type == "Login") Screen.Login
                else if (type == "RegisterUser") Screen.RegisterUser
                else if (type == "ChangePassword") Screen.ChangePassword
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
                        remoteId = (list[10] as? String)?.ifBlank { null },
                        featurePermissions = com.checklistboteco.domain.model.FeaturePermissions(
                            canRegisterUsers = list[11] as Boolean,
                            canCreateActivities = list[12] as Boolean,
                            canEditUsers = list[13] as Boolean,
                            canCreateInventoryCounts = list.getOrNull(14) as? Boolean ?: false,
                            canViewInventoryInsights = list.getOrNull(15) as? Boolean ?: false,
                            canManageAdministrativeStock = list.getOrNull(16) as? Boolean ?: false
                        )
                    )
                    Screen.Main(
                        user = user,
                        authToken = (list.getOrNull(17) as? String)?.ifBlank { null }
                            ?: ((list.getOrNull(16) as? String)?.ifBlank { null }),
                        remoteUserId = (list.getOrNull(18) as? String)?.ifBlank { null }
                            ?: ((list.getOrNull(17) as? String)?.ifBlank { null })
                    )
                }
            }
        )

        var currentScreen by rememberSaveable(stateSaver = screenSaver) { 
            mutableStateOf<Screen>(Screen.Login) 
        }
        val loginViewModel = remember(repository, backendApiClient, syncCoordinator) {
            LoginViewModel(repository, backendApiClient, syncCoordinator)
        }

        LaunchedEffect(loginViewModel) {
            SessionExpiredNotifier.events.collect { event ->
                loginViewModel.showSessionExpiredMessage(event.reason)
                loginViewModel.logout()
                currentScreen = Screen.Login
                SessionExpiredNotifier.reset()
            }
        }

        when (val s = currentScreen) {
            is Screen.Login -> {
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = { user ->
                        val state = loginViewModel.uiState.value
                        currentScreen = Screen.Main(user, state.authToken, state.remoteUserId)
                    },
                    onPasswordChangeRequired = {
                        currentScreen = Screen.ChangePassword
                    },
                    onNewUserClick = { currentScreen = Screen.RegisterUser }
                )
            }

            is Screen.ChangePassword -> {
                ChangePasswordScreen(
                    viewModel = loginViewModel,
                    onPasswordChanged = { user ->
                        val state = loginViewModel.uiState.value
                        currentScreen = Screen.Main(user, state.authToken, state.remoteUserId)
                    }
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
                    syncCoordinator = syncCoordinator,
                    backendApiClient = backendApiClient,
                    authToken = s.authToken,
                    remoteUserId = s.remoteUserId,
                    onLogout = {
                        loginViewModel.logout()
                        currentScreen = Screen.Login
                    }
                )
            }
        }
        GlobalAppFeedback()
        }
    }
}
