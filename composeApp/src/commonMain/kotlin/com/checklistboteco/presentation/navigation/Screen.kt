package com.checklistboteco.presentation.navigation

import com.checklistboteco.domain.model.User

sealed class Screen {
    data object Login : Screen()
    data object RegisterUser : Screen()
    data object ChangePassword : Screen()
    data class Main(
        val user: User,
        val authToken: String? = null,
        val remoteUserId: String? = null
    ) : Screen()
}
