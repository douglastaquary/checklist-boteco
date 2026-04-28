package com.checklistboteco.presentation.navigation

import com.checklistboteco.domain.model.User

sealed class Screen {
    data object Login : Screen()
    data class Main(val user: User) : Screen()
}
