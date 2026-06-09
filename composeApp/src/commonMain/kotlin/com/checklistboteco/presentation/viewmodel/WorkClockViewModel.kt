package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.GeoPoint
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.WorkClockCalculator
import com.checklistboteco.domain.model.WorkClockEntry
import com.checklistboteco.domain.model.WorkClockSummary
import com.checklistboteco.domain.model.WorkClockType
import com.checklistboteco.domain.model.WorksiteLocation
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
    val currentLocation: GeoPoint = WorksiteLocation.point,
    val distanceFromWorkMeters: Double = 0.0,
    val currentTimestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val canClockIn: Boolean = false,
    val isAdmin: Boolean = false,
    val showDetails: Boolean = false,
    val feedback: String? = null
)

class WorkClockViewModel(
    private val repository: ChecklistRepository,
    private val user: User,
    private val scope: CoroutineScope
) {
    private val timeZone = TimeZone.currentSystemDefault()
    private val _uiState = MutableStateFlow(
        WorkClockUiState(
            isAdmin = user.permissionLevel == PermissionLevel.ADMIN,
            canClockIn = canUseClock(WorksiteLocation.point)
        )
    )
    val uiState: StateFlow<WorkClockUiState> = _uiState.asStateFlow()

    init {
        loadTodayEntries()
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
                val now = Clock.System.now().toEpochMilliseconds()
                val distance = WorkClockCalculator.distanceMeters(_uiState.value.currentLocation, WorksiteLocation.point)

                _uiState.update {
                    it.copy(
                        entries = entries,
                        nextType = nextType,
                        summary = WorkClockCalculator.summarizeDay(entries, weeklyWorked),
                        currentTimestamp = now,
                        distanceFromWorkMeters = distance,
                        canClockIn = canUseClock(it.currentLocation)
                    )
                }
            }
        }
    }

    fun confirmClockIn() {
        val state = _uiState.value
        if (!state.canClockIn) {
            _uiState.update { it.copy(feedback = "A marcação só é liberada dentro de ${WorksiteLocation.allowedRadiusMeters.toInt()} metros do local de trabalho") }
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val isLate = WorkClockCalculator.isLateEntry()
        repository.insertWorkClockEntry(
            userId = user.id,
            type = state.nextType,
            registeredAt = now,
            location = state.currentLocation,
            distanceFromWorkMeters = state.distanceFromWorkMeters,
            isLate = isLate
        )
        _uiState.update {
            it.copy(feedback = "${state.nextType.displayName} registrada")
        }
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

    private fun canUseClock(location: GeoPoint): Boolean {
        if (user.permissionLevel == PermissionLevel.ADMIN) return false
        return WorkClockCalculator.distanceMeters(location, WorksiteLocation.point) <= WorksiteLocation.allowedRadiusMeters
    }

    private fun Long.toLocalDateKey(): String {
        return kotlinx.datetime.Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone).date.toString()
    }
}
