package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.remote.RemoteLoginResult
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.data.sync.SyncSession
import com.checklistboteco.domain.model.User
import com.checklistboteco.platform.AppErrorMapper
import com.checklistboteco.platform.AppNetworkFeedback
import com.checklistboteco.platform.DeviceIdentity
import com.checklistboteco.platform.LoginCredentialsStorage
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
    val rememberCredentials: Boolean = false,
    val pendingBiometricUnlock: Boolean = false,
    val biometricUnlockInProgress: Boolean = false,
    val twoFactorCode: String = "",
    val requiresTwoFactor: Boolean = false,
    val challengeId: String? = null,
    val developmentCode: String? = null,
    val error: String? = null,
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
        scope.launch { restoreSavedCredentials(autoUnlock = true) }
    }

    fun updateUserName(name: String) {
        _uiState.update { it.copy(userName = name, error = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun updateRememberCredentials(remember: Boolean) {
        _uiState.update { it.copy(rememberCredentials = remember) }
    }

    fun updateTwoFactorCode(code: String) {
        _uiState.update { it.copy(twoFactorCode = code.filter(Char::isDigit).take(6), error = null) }
    }

    fun unlockRememberedUser() {
        scope.launch { restoreSavedCredentials(autoUnlock = true) }
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

        scope.launch {
            persistCredentials()
            _uiState.update {
                it.copy(
                    currentUser = user,
                    isLoggedIn = true,
                    error = null
                )
            }
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

        _uiState.update { it.copy(error = null) }
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
                    AppNetworkFeedback.showError(AppErrorMapper.toUserMessage(error))
                }
            )
        }
    }

    fun logout() {
        repository.clearSyncSession()
        scope.launch { restoreSavedCredentials(autoUnlock = true) }
    }

    private suspend fun restoreSavedCredentials(autoUnlock: Boolean) {
        val saved = LoginCredentialsStorage.load()
        if (saved.requiresBiometricUnlock) {
            _uiState.update {
                LoginUiState(
                    rememberCredentials = true,
                    pendingBiometricUnlock = true
                )
            }
            if (autoUnlock) {
                unlockRememberedCredentials()
            }
            return
        }

        _uiState.update {
            LoginUiState(
                userName = saved.username,
                password = saved.password,
                rememberCredentials = saved.remember
            )
        }
    }

    private suspend fun unlockRememberedCredentials() {
        _uiState.update {
            it.copy(
                biometricUnlockInProgress = true,
                error = null
            )
        }

        LoginCredentialsStorage.unlockRememberedCredentials()
            .onSuccess { credentials ->
                _uiState.update {
                    it.copy(
                        userName = credentials.username,
                        password = credentials.password,
                        rememberCredentials = true,
                        pendingBiometricUnlock = false,
                        biometricUnlockInProgress = false
                    )
                }
            }
            .onFailure { error ->
                val message = error.message.orEmpty()
                val userMessage = when {
                    message.contains("cancel", ignoreCase = true) ||
                        message.contains("Cancelar", ignoreCase = true) ->
                        "Confirme a biometria para preencher usuário e senha."
                    message.isNotBlank() -> message
                    else -> "Não foi possível desbloquear o login salvo."
                }
                _uiState.update {
                    it.copy(
                        pendingBiometricUnlock = true,
                        biometricUnlockInProgress = false,
                        error = userMessage
                    )
                }
            }
    }

    private fun loginWithBackend(email: String, password: String) {
        val api = backendApiClient ?: return
        _uiState.update { it.copy(error = null) }
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
                    AppNetworkFeedback.showError(AppErrorMapper.toUserMessage(error))
                }
            )
        }
    }

    private fun completeRemoteLogin(result: RemoteLoginResult) {
        val api = backendApiClient ?: return
        val token = result.token
        val remoteUserId = result.remoteUserId
        if (remoteUserId.isNullOrBlank() || token.isNullOrBlank()) {
            AppNetworkFeedback.showError("Resposta de login inválida. Tente novamente.")
            return
        }

        scope.launch {
            val authoritative = runCatching { api.fetchCurrentUser(token) }.getOrElse { result }
            val remoteUser = authoritative.user ?: result.user
            if (remoteUser == null) {
                AppNetworkFeedback.showError("Resposta de login inválida. Tente novamente.")
                return@launch
            }

            val profile = remoteUser.copy(remoteId = remoteUserId)
            val localUser = repository.getUserByRemoteId(remoteUserId)
                ?: repository.getUserByEmail(profile.email)
                ?: repository.getUserByName(profile.name)
                ?: run {
                    repository.insertUser(
                        name = profile.name,
                        email = profile.email,
                        password = _uiState.value.password,
                        area = profile.area,
                        workSector = profile.workSector,
                        permissionLevel = profile.permissionLevel,
                        allowedAreas = profile.allowedAreas,
                        createdAt = profile.createdAt,
                        remoteId = remoteUserId,
                        featurePermissions = profile.featurePermissions
                    )
                    repository.getUserByRemoteId(remoteUserId)
                        ?: repository.getUserByEmail(profile.email)
                        ?: repository.getUserByName(profile.name)
                }

            if (localUser == null) {
                AppNetworkFeedback.showError("Não foi possível preparar o usuário local. Tente novamente.")
                return@launch
            }

            val syncedUser = repository.syncLocalUserFromRemote(
                localUserId = localUser.id,
                remoteUser = profile
            ) ?: localUser.copy(
                remoteId = remoteUserId,
                featurePermissions = profile.featurePermissions
            )

            repository.saveSyncSession(
                localUserId = syncedUser.id,
                session = SyncSession(
                    authToken = token,
                    remoteUserId = remoteUserId
                )
            )

            persistCredentials()

            _uiState.update {
                it.copy(
                    currentUser = syncedUser.copy(remoteId = remoteUserId),
                    authToken = token,
                    remoteUserId = remoteUserId,
                    requiresTwoFactor = false,
                    isLoggedIn = true,
                    error = null
                )
            }
        }
    }

    private suspend fun persistCredentials() {
        val state = _uiState.value
        LoginCredentialsStorage.save(
            username = state.userName,
            password = state.password,
            remember = state.rememberCredentials
        ).onFailure { error ->
            val message = error.message.orEmpty()
            if (message.contains("cancel", ignoreCase = true) ||
                message.contains("Cancelar", ignoreCase = true)
            ) {
                _uiState.update {
                    it.copy(
                        rememberCredentials = false,
                        error = "Login não salvo. Confirme a biometria para lembrar usuário e senha."
                    )
                }
            }
        }
    }
}
