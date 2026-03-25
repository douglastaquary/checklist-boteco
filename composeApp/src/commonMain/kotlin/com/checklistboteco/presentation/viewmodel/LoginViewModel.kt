package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val userName: String = "",
    val password: String = "",
    val error: String? = null,
    val currentUser: User? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(
    private val repository: ChecklistRepository
) {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        repository.seedInitialData()
    }

    fun updateUserName(name: String) {
        _uiState.update { it.copy(userName = name, error = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun login() {
        val name = _uiState.value.userName.trim()
        val password = _uiState.value.password

        if (name.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Preencha usuário e senha") }
            return
        }

        val user = repository.getUserByName(name)
        if (user == null || user.password != password) {
            _uiState.update { it.copy(error = "Usuário ou senha inválidos") }
            return
        }

        _uiState.update { 
            it.copy(
                currentUser = user,
                isLoggedIn = true,
                error = null
            )
        }
    }

    fun logout() {
        _uiState.update { 
            LoginUiState()
        }
    }
}
