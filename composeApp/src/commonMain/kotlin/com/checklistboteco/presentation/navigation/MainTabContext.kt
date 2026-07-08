package com.checklistboteco.presentation.navigation

import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.data.sync.SyncCoordinator
import com.checklistboteco.domain.model.User

data class MainTabContext(
    val user: User,
    val repository: ChecklistRepository,
    val syncCoordinator: SyncCoordinator?,
    val backendApiClient: BackendApiClient?,
    val authToken: String?,
    val remoteUserId: String?,
    val onLogout: () -> Unit
)
