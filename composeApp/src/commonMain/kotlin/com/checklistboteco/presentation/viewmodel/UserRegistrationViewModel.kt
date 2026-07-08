package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.UserRegistrationInput
import com.checklistboteco.domain.model.UserRegistrationValidator
import com.checklistboteco.domain.model.WorkSector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserRegistrationUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val workSector: WorkSector = WorkSector.ATENDIMENTO,
    val password: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val isSaved: Boolean = false
)

class UserRegistrationViewModel(
    private val repository: ChecklistRepository,
    private val backendApiClient: BackendApiClient? = null,
    private val authToken: String? = null,
    private val scope: CoroutineScope? = null
) {
    private val _uiState = MutableStateFlow(UserRegistrationUiState())
    val uiState: StateFlow<UserRegistrationUiState> = _uiState.asStateFlow()

    fun updateFirstName(value: String) {
        _uiState.update { it.copy(firstName = value, error = null, isSaved = false) }
    }

    fun updateLastName(value: String) {
        _uiState.update { it.copy(lastName = value, error = null, isSaved = false) }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, error = null, isSaved = false) }
    }

    fun updateWorkSector(value: WorkSector) {
        _uiState.update { it.copy(workSector = value, error = null, isSaved = false) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, error = null, isSaved = false) }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value, error = null, isSaved = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun save() {
        val state = _uiState.value
        val result = UserRegistrationValidator.validate(
            UserRegistrationInput(
                firstName = state.firstName,
                lastName = state.lastName,
                email = state.email,
                workSector = state.workSector,
                password = state.password,
                confirmPassword = state.confirmPassword
            )
        )

        result.fold(
            onSuccess = { user ->
                val api = backendApiClient
                val token = authToken
                if (api != null) {
                    if (token.isNullOrBlank()) {
                        _uiState.update {
                            it.copy(error = "Com a API ativa, solicite cadastro ao administrador.")
                        }
                        return
                    }
                    val registrationScope = scope
                    if (registrationScope == null) {
                        _uiState.update { it.copy(error = "Não foi possível cadastrar via API.") }
                        return
                    }
                    registrationScope.launch {
                        runCatching {
                            api.createUser(
                                token = token,
                                name = user.fullName,
                                email = user.email,
                                password = user.password,
                                workSector = user.workSector
                            )
                        }.onSuccess { remoteUser ->
                            repository.upsertRemoteUser(remoteUser)
                            _uiState.update { UserRegistrationUiState(isSaved = true) }
                        }.onFailure { error ->
                            _uiState.update {
                                it.copy(error = error.message ?: "Não foi possível cadastrar o usuário")
                            }
                        }
                    }
                    return
                }
                if (repository.getUserByName(user.fullName) != null || repository.getUserByEmail(user.email) != null) {
                    _uiState.update { it.copy(error = "Usuário já cadastrado") }
                    return
                }
                repository.insertRegisteredUser(user)
                _uiState.update { UserRegistrationUiState(isSaved = true) }
            },
            onFailure = { failure ->
                _uiState.update { it.copy(error = failure.message ?: "Não foi possível cadastrar o usuário") }
            }
        )
    }
}
