package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.platform.AppErrorMapper
import com.checklistboteco.platform.RemoteSessionRequiredException
import com.checklistboteco.platform.requireRemoteToken
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.FeaturePermissions
import com.checklistboteco.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionManagementUiState(
    val users: List<User> = emptyList(),
    val selectedUser: User? = null,
    val canManagePermissions: Boolean = false,
    val error: String? = null
)

class PermissionManagementViewModel(
    private val repository: ChecklistRepository,
    private val currentUser: User,
    private val backendApiClient: BackendApiClient?,
    private val authToken: String?,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(
        PermissionManagementUiState(canManagePermissions = currentUser.canManagePermissions())
    )
    val uiState: StateFlow<PermissionManagementUiState> = _uiState.asStateFlow()

    init {
        if (currentUser.canManagePermissions()) {
            loadUsers()
        }
    }

    private fun loadUsers() {
        scope.launch {
            val api = backendApiClient
            val token = authToken
            if (api != null) {
                runCatching {
                    val remoteToken = requireRemoteToken(api, token)
                    api.listUsers(remoteToken)
                }
                    .onSuccess { remoteUsers ->
                        repository.upsertRemoteUsers(remoteUsers)
                        _uiState.update { it.copy(error = null) }
                    }
                    .onFailure { error ->
                        if (error is RemoteSessionRequiredException) return@onFailure
                        _uiState.update { it.copy(error = AppErrorMapper.toUserMessage(error)) }
                    }
            }
            repository.getAllUsers().collect { users ->
                _uiState.update { state ->
                    state.copy(
                        users = users,
                        selectedUser = users.firstOrNull { it.id == state.selectedUser?.id } ?: state.selectedUser
                    )
                }
            }
        }
    }

    fun selectUser(user: User) {
        if (!currentUser.canManagePermissions()) return
        _uiState.update { it.copy(selectedUser = user, error = null) }
    }

    fun closeDetails() {
        _uiState.update { it.copy(selectedUser = null, error = null) }
    }

    fun updateCanRegisterUsers(value: Boolean) {
        updateSelectedPermissions { it.copy(canRegisterUsers = value) }
    }

    fun updateCanCreateActivities(value: Boolean) {
        updateSelectedPermissions { it.copy(canCreateActivities = value) }
    }

    fun updateCanEditUsers(value: Boolean) {
        updateSelectedPermissions { it.copy(canEditUsers = value) }
    }
    fun updateCanCreateInventoryCounts(value:Boolean){ updateSelectedPermissions { it.copy(canCreateInventoryCounts=value) } }
    fun updateCanViewInventoryInsights(value:Boolean){ updateSelectedPermissions { it.copy(canViewInventoryInsights=value) } }
    fun updateCanManageAdministrativeStock(value:Boolean){ updateSelectedPermissions { it.copy(canManageAdministrativeStock=value) } }
    fun updateCanImportPurchases(value:Boolean){ updateSelectedPermissions { it.copy(canImportPurchases=value) } }

    private fun updateSelectedPermissions(transform: (FeaturePermissions) -> FeaturePermissions) {
        if (!currentUser.canManagePermissions()) {
            _uiState.update { it.copy(error = "Somente administradores podem alterar permissões") }
            return
        }

        val user = _uiState.value.selectedUser ?: return
        val permissions = transform(user.featurePermissions)
        val api = backendApiClient
        val token = authToken
        val remoteId = user.remoteId
        if (api != null && !remoteId.isNullOrBlank()) {
            scope.launch {
                runCatching {
                    val remoteToken = requireRemoteToken(api, token)
                    api.updateUserPermissions(remoteToken, remoteId, permissions)
                }
                    .onSuccess { updated ->
                        repository.updateUserFeaturePermissions(user.id, updated.featurePermissions)
                        applyUpdatedUser(user.copy(featurePermissions = updated.featurePermissions))
                    }
                    .onFailure { error ->
                        if (error is RemoteSessionRequiredException) return@onFailure
                        _uiState.update { it.copy(error = AppErrorMapper.toUserMessage(error)) }
                    }
            }
            return
        }
        repository.updateUserFeaturePermissions(user.id, permissions)
        applyUpdatedUser(user.copy(featurePermissions = permissions))
    }

    private fun applyUpdatedUser(updatedUser: User) {
        _uiState.update { state ->
            state.copy(
                selectedUser = updatedUser,
                users = state.users.map { if (it.id == updatedUser.id) updatedUser else it },
                error = null
            )
        }
    }
}
