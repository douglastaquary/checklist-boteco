package com.checklistboteco.presentation.navigation

import com.checklistboteco.domain.model.User

sealed class Screen {
    data object Login : Screen()
    data class Checklist(val user: User) : Screen()
    data class Admin(val user: User) : Screen()
    data class Dashboard(val user: User) : Screen()
    data class ActivitiesManagement(val user: User) : Screen()
    data class UserManagement(val user: User) : Screen()
}
