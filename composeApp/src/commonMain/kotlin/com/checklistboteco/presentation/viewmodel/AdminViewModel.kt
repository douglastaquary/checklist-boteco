package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.Area
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class AreaStats(
    val area: Area,
    val total: Int,
    val completed: Int,
    val pending: Int
)

data class AdminUiState(
    val statsByArea: Map<Area, AreaStats> = emptyMap(),
    val isLoading: Boolean = false
)

class AdminViewModel(
    private val repository: ChecklistRepository,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        scope.launch {
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val monthStr = today.monthNumber.toString().padStart(2, '0')
            val periodStart = kotlinx.datetime.Instant.parse(
                "${today.year}-$monthStr-01T00:00:00Z"
            ).toEpochMilliseconds()

            repository.getCompletionStatsByArea(periodStart).collect { statsMap ->
            val areaStats = statsMap.map { (area, pair) ->
                area to AreaStats(
                    area = area,
                    total = pair.first,
                    completed = pair.second,
                    pending = pair.first - pair.second
                )
            }.toMap()
                _uiState.update { it.copy(statsByArea = areaStats) }
            }
        }
    }
}
