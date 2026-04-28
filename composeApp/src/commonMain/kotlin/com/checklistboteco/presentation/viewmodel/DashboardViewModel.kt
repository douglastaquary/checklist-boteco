package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.data.repository.GlobalDashboardStats
import com.checklistboteco.data.repository.UserRanking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class DashboardUiState(
    val globalStats: GlobalDashboardStats? = null,
    val ranking: List<UserRanking> = emptyList(),
    val isLoading: Boolean = false
)

class DashboardViewModel(
    private val repository: ChecklistRepository,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val monthStr = today.monthNumber.toString().padStart(2, '0')
            val periodStart = kotlinx.datetime.Instant.parse(
                "${today.year}-$monthStr-01T00:00:00Z"
            ).toEpochMilliseconds()

            repository.getGlobalStats(periodStart).collect { stats ->
                _uiState.update { it.copy(globalStats = stats) }
            }
        }

        scope.launch {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val monthStr = today.monthNumber.toString().padStart(2, '0')
            val periodStart = kotlinx.datetime.Instant.parse(
                "${today.year}-$monthStr-01T00:00:00Z"
            ).toEpochMilliseconds()

            repository.getRanking(periodStart).collect { ranking ->
                _uiState.update { it.copy(ranking = ranking, isLoading = false) }
            }
        }
    }
}
