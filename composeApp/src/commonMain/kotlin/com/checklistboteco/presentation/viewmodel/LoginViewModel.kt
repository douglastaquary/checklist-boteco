package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.remote.RemoteLoginResult
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.User
import com.checklistboteco.platform.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val userName: String = "",
    val password: String = "",
    val twoFactorCode: String = "",
    val requiresTwoFactor: Boolean = false,
    val challengeId: String? = null,
    val developmentCode: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
    val currentUser: User? = null,
    val authToken: String? = null,
    val remoteUserId: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(
    private val repository: ChecklistRepository,
    private val backendApiClient: BackendApiClient? = BackendApiClient.fromEnvironment(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    fun updateTwoFactorCode(code: String) {
        _uiState.update { it.copy(twoFactorCode = code.filter(Char::isDigit).take(6), error = null) }
    }

    fun login() {
        val name = _uiState.value.userName.trim()
        val password = _uiState.value.password

        if (name.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Preencha usuário e senha") }
            return
        }

        if (backendApiClient != null) {
            loginWithBackend(name, password)
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

    fun verifyTwoFactor() {
        val state = _uiState.value
        val api = backendApiClient ?: return
        val challengeId = state.challengeId
        if (challengeId.isNullOrBlank() || state.twoFactorCode.length != 6) {
            _uiState.update { it.copy(error = "Informe o código de 6 dígitos") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            runCatching {
                api.verifyDevice(
                    challengeId = challengeId,
                    code = state.twoFactorCode,
                    deviceId = DeviceIdentity.getOrCreateDeviceId(),
                    deviceName = DeviceIdentity.deviceName()
                )
            }.fold(
                onSuccess = ::completeRemoteLogin,
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Não foi possível validar o dispositivo")
                    }
                }
            )
        }
    }

    fun logout() {
        _uiState.update { 
            LoginUiState()
        }
    }

    private fun loginWithBackend(email: String, password: String) {
        val api = backendApiClient ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            runCatching {
                api.login(
                    email = email,
                    password = password,
                    deviceId = DeviceIdentity.getOrCreateDeviceId(),
                    deviceName = DeviceIdentity.deviceName()
                )
            }.fold(
                onSuccess = { result ->
                    if (result.requiresTwoFactor) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                requiresTwoFactor = true,
                                challengeId = result.challengeId,
                                developmentCode = result.developmentCode,
                                error = result.developmentCode?.let { code -> "Código de desenvolvimento: $code" }
                                    ?: result.deliveryHint
                                    ?: "Confirme este dispositivo"
                            )
                        }
                    } else {
                        completeRemoteLogin(result)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Não foi possível entrar pela API")
                    }
                }
            )
        }
    }

    private fun completeRemoteLogin(result: RemoteLoginResult) {
        val remoteUser = result.user
        val token = result.token
        val remoteUserId = result.remoteUserId
        if (remoteUser == null || token.isNullOrBlank() || remoteUserId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Resposta de login inválida") }
            return
        }

        val localUser = repository.getUserByEmail(remoteUser.email)
            ?: repository.getUserByName(remoteUser.name)
            ?: run {
                repository.insertUser(
                    name = remoteUser.name,
                    email = remoteUser.email,
                    password = _uiState.value.password,
                    area = remoteUser.area,
                    workSector = remoteUser.workSector,
                    permissionLevel = remoteUser.permissionLevel,
                    allowedAreas = remoteUser.allowedAreas,
                    createdAt = remoteUser.createdAt,
                    featurePermissions = remoteUser.featurePermissions
                )
                repository.getUserByEmail(remoteUser.email) ?: repository.getUserByName(remoteUser.name)
            }

        if (localUser == null) {
            _uiState.update { it.copy(isLoading = false, error = "Não foi possível preparar o usuário local") }
            return
        }

        _uiState.update {
            it.copy(
                currentUser = localUser,
                authToken = token,
                remoteUserId = remoteUserId,
                requiresTwoFactor = false,
                isLoggedIn = true,
                isLoading = false,
                error = null
            )
        }
    }
}
