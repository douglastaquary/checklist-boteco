package com.checklistboteco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.checklistboteco.presentation.screen.LoginScreen
import com.checklistboteco.presentation.screen.MainScreen
import com.checklistboteco.presentation.theme.ChecklistBotecoTheme
import com.checklistboteco.presentation.viewmodel.LoginViewModel

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

        val screenSaver = listSaver<Screen, Any>(
            save = { screen ->
                when (screen) {
                    is Screen.Login -> listOf("Login")
                    is Screen.Main -> listOf(
                        "Main",
                        screen.user.id,
                        screen.user.name,
                        screen.user.password,
                        screen.user.area.name,
                        screen.user.permissionLevel.name,
                        screen.user.allowedAreas.joinToString(",") { it.name }
                    )
                }
            },
            restore = { list ->
                val type = list[0] as String
                if (type == "Login") Screen.Login
                else {
                    val user = User(
                        id = list[1] as Long,
                        name = list[2] as String,
                        password = list[3] as String,
                        area = Area.fromString(list[4] as String),
                        permissionLevel = PermissionLevel.fromString(list[5] as String),
                        allowedAreas = (list[6] as String).split(",")
                            .filter { it.isNotEmpty() }
                            .map { Area.fromString(it) }
                    )
                    Screen.Main(user)
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
                    onLoginSuccess = { user -> currentScreen = Screen.Main(user) }
                )
            }

            is Screen.Main -> {
                MainScreen(
                    user = s.user,
                    repository = repository,
                    onLogout = { currentScreen = Screen.Login }
                )
            }
        }
    }
}
