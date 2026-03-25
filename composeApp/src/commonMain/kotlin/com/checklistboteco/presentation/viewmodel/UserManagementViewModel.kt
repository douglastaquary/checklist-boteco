package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserManagementUiState(
    val users: List<User> = emptyList(),
    val showAddDialog: Boolean = false,
    val newUserName: String = "",
    val newUserPassword: String = "",
    val newUserArea: Area = Area.ATENDIMENTO,
    val newUserPermissionLevel: PermissionLevel = PermissionLevel.USER,
    val newUserAllowedAreas: Set<Area> = emptySet(),
    val error: String? = null
)

class UserManagementViewModel(
    private val repository: ChecklistRepository,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        scope.launch {
            repository.getAllUsers().collect { users ->
                _uiState.update { it.copy(users = users) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                newUserName = "",
                newUserPassword = "",
                newUserArea = Area.ATENDIMENTO,
                newUserPermissionLevel = PermissionLevel.USER,
                newUserAllowedAreas = setOf(Area.ATENDIMENTO),
                error = null
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun updateNewUserName(name: String) {
        _uiState.update { it.copy(newUserName = name, error = null) }
    }

    fun updateNewUserPassword(password: String) {
        _uiState.update { it.copy(newUserPassword = password, error = null) }
    }

    fun updateNewUserArea(area: Area) {
        _uiState.update { it.copy(newUserArea = area) }
    }

    fun updateNewUserPermissionLevel(level: PermissionLevel) {
        _uiState.update { it.copy(newUserPermissionLevel = level) }
    }

    fun toggleAllowedArea(area: Area) {
        _uiState.update { state ->
            val newAreas = if (area in state.newUserAllowedAreas) {
                state.newUserAllowedAreas - area
            } else {
                state.newUserAllowedAreas + area
            }
            state.copy(newUserAllowedAreas = newAreas)
        }
    }

    fun addUser() {
        val name = _uiState.value.newUserName.trim()
        val password = _uiState.value.newUserPassword
        val allowedAreas = _uiState.value.newUserAllowedAreas

        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Digite o nome do usuário") }
            return
        }
        if (password.isBlank()) {
            _uiState.update { it.copy(error = "Digite a senha") }
            return
        }
        if (repository.getUserByName(name) != null) {
            _uiState.update { it.copy(error = "Usuário já existe") }
            return
        }
        if (_uiState.value.newUserPermissionLevel == PermissionLevel.USER && allowedAreas.isEmpty()) {
            _uiState.update { it.copy(error = "Selecione ao menos uma área de acesso") }
            return
        }

        val areasToUse = if (_uiState.value.newUserPermissionLevel == PermissionLevel.ADMIN) {
            Area.entries.toList()
        } else {
            allowedAreas.toList()
        }

        repository.insertUser(
            name = name,
            password = password,
            area = _uiState.value.newUserArea,
            permissionLevel = _uiState.value.newUserPermissionLevel,
            allowedAreas = areasToUse
        )
        _uiState.update {
            it.copy(showAddDialog = false, error = null)
        }
        loadUsers()
    }
}
