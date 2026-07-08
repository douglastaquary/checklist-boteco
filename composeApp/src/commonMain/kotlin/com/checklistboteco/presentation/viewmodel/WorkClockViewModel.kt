package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.BackendApiClient
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.GeoPoint
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.WorkClockCalculator
import com.checklistboteco.domain.model.WorkClockEntry
import com.checklistboteco.domain.model.WorkClockSummary
import com.checklistboteco.domain.model.WorkClockType
import com.checklistboteco.domain.model.WorksiteLocation
import com.checklistboteco.platform.DeviceIdentity
import com.checklistboteco.platform.requireRemoteToken
import com.checklistboteco.platform.LocationProvider
import com.checklistboteco.platform.LocationUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class WorkClockUiState(
    val entries: List<WorkClockEntry> = emptyList(),
    val nextType: WorkClockType = WorkClockType.ENTRADA,
    val summary: WorkClockSummary = WorkClockCalculator.summarizeDay(emptyList(), 0L),
    val currentLocation: GeoPoint? = null,
    val locationAccuracyMeters: Float? = null,
    val distanceFromWorkMeters: Double = Double.MAX_VALUE,
    val currentTimestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val canClockIn: Boolean = false,
    val locationStatus: String = "Aguardando GPS…",
    val isAdmin: Boolean = false,
    val deviceId: String = "",
    val deviceName: String = "",
    val showDetails: Boolean = false,
    val feedback: String? = null
)

class WorkClockViewModel(
    private val repository: ChecklistRepository,
    private val user: User,
    private val scope: CoroutineScope,
    private val backendApiClient: BackendApiClient? = null,
    private val authToken: String? = null,
    private val remoteUserId: String? = null
) {
    private val timeZone = TimeZone.currentSystemDefault()
    private val _uiState = MutableStateFlow(
        WorkClockUiState(
            isAdmin = user.permissionLevel == PermissionLevel.ADMIN,
            deviceId = DeviceIdentity.getOrCreateDeviceId(),
            deviceName = DeviceIdentity.deviceName()
        )
    )
    val uiState: StateFlow<WorkClockUiState> = _uiState.asStateFlow()

    init {
        loadTodayEntries()
        retryPendingEntries()
    }

    fun startLocationUpdates() {
        LocationProvider.startUpdates(::onLocationUpdate)
    }

    fun stopLocationUpdates() {
        LocationProvider.stopUpdates()
    }

    fun onLocationPermissionChanged(granted: Boolean) {
        if (!granted) {
            _uiState.update {
                it.copy(
                    locationStatus = "Permita o acesso ao GPS para registrar ponto.",
                    canClockIn = false
                )
            }
            return
        }
        startLocationUpdates()
    }

    private fun onLocationUpdate(update: LocationUpdate) {
        val point = update.point
        val accuracy = update.accuracyMeters
        val distance = point?.let { WorkClockCalculator.distanceMeters(it, WorksiteLocation.point) } ?: Double.MAX_VALUE
        val canClock = canUseClock(point, accuracy, distance)
        val status = when {
            point == null -> "Aguardando sinal GPS…"
            accuracy != null && accuracy > 20f -> "Aguardando precisão do GPS (≤ 20 m)…"
            distance > WorksiteLocation.allowedRadiusMeters ->
                "Fora do raio de ${WorksiteLocation.allowedRadiusMeters.toInt()} m (${distance.toInt()} m)."
            else -> "Dentro do raio permitido."
        }
        _uiState.update {
            it.copy(
                currentLocation = point,
                locationAccuracyMeters = accuracy,
                distanceFromWorkMeters = distance,
                canClockIn = canClock,
                locationStatus = status,
                currentTimestamp = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    private fun loadTodayEntries() {
        scope.launch {
            val today = Clock.System.now().toLocalDateTime(timeZone).date
            repository.getWorkClockEntriesByUserAndDate(user.id, today).collect { entries ->
                val weeklyWorked = repository.getWorkClockEntriesByUserAndCurrentWeek(user.id)
                    .groupBy { it.registeredAt.toLocalDateKey() }
                    .values
                    .sumOf { WorkClockCalculator.summarizeDay(it).workedMillis }
                val nextType = WorkClockCalculator.nextType(entries)
                val state = _uiState.value

                _uiState.update {
                    it.copy(
                        entries = entries,
                        nextType = nextType,
                        summary = WorkClockCalculator.summarizeDay(entries, weeklyWorked),
                        currentTimestamp = Clock.System.now().toEpochMilliseconds(),
                        canClockIn = canUseClock(state.currentLocation, state.locationAccuracyMeters, state.distanceFromWorkMeters)
                    )
                }
            }
        }
    }

    fun confirmClockIn() {
        val state = _uiState.value
        if (!state.canClockIn) {
            _uiState.update {
                it.copy(feedback = state.locationStatus.ifBlank {
                    "A marcação só é liberada dentro de ${WorksiteLocation.allowedRadiusMeters.toInt()} metros do local de trabalho"
                })
            }
            return
        }
        val location = state.currentLocation ?: return

        val now = Clock.System.now().toEpochMilliseconds()
        val isLate = WorkClockCalculator.isLateEntry()
        val localId = repository.insertWorkClockEntry(
            userId = user.id,
            type = state.nextType,
            registeredAt = now,
            location = location,
            distanceFromWorkMeters = state.distanceFromWorkMeters,
            isLate = isLate
        )
        _uiState.update {
            it.copy(feedback = "${state.nextType.displayName} registrada")
        }
        syncWorkClockEntry(state, localId, location, now, isLate)
    }

    fun showDetails() {
        _uiState.update { it.copy(showDetails = true) }
    }

    fun hideDetails() {
        _uiState.update { it.copy(showDetails = false) }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    private fun retryPendingEntries() {
        val api = backendApiClient ?: return
        scope.launch {
            runCatching {
                val token = requireRemoteToken(api, authToken)
                val backendUserId = remoteUserId ?: return@runCatching
                repository.retryPendingWorkClockEntries(
                    userId = user.id,
                    remoteUserId = backendUserId,
                    deviceId = DeviceIdentity.getOrCreateDeviceId(),
                    token = token,
                    api = api
                )
            }
        }
    }

    private fun canUseClock(location: GeoPoint?, accuracy: Float?, distance: Double): Boolean {
        if (user.permissionLevel == PermissionLevel.ADMIN) return false
        if (location == null) return false
        if (accuracy != null && accuracy > 20f) return false
        return distance <= WorksiteLocation.allowedRadiusMeters
    }

    private fun syncWorkClockEntry(
        state: WorkClockUiState,
        localId: Long,
        location: GeoPoint,
        registeredAt: Long,
        isLate: Boolean
    ) {
        val api = backendApiClient ?: return
        scope.launch {
            runCatching {
                val token = requireRemoteToken(api, authToken)
                val backendUserId = remoteUserId ?: return@runCatching
                val remoteId = api.pushWorkClockEntry(
                    token = token,
                    deviceId = DeviceIdentity.getOrCreateDeviceId(),
                    remoteUserId = backendUserId,
                    _localEntryId = localId,
                    type = state.nextType,
                    registeredAt = registeredAt,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    distanceFromWorkMeters = state.distanceFromWorkMeters,
                    isLate = isLate
                )
                repository.markWorkClockEntrySynced(localId, remoteId)
            }.onFailure {
                _uiState.update { current ->
                    current.copy(feedback = "${state.nextType.displayName} registrada localmente. Sincronização pendente.")
                }
            }
        }
    }

    private fun Long.toLocalDateKey(): String {
        return kotlinx.datetime.Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone).date.toString()
    }
}
