package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.Activity
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.Frequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivitiesManagementUiState(
    val activities: List<Activity> = emptyList(),
    val showAddDialog: Boolean = false,
    val newActivityName: String = "",
    val newActivityArea: Area = Area.ATENDIMENTO,
    val newActivityFrequency: Frequency = Frequency.DIARIO,
    val newActivityEffort: Int = 1,
    val error: String? = null
)

class ActivitiesManagementViewModel(
    private val repository: ChecklistRepository,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(ActivitiesManagementUiState())
    val uiState: StateFlow<ActivitiesManagementUiState> = _uiState.asStateFlow()

    init {
        loadActivities()
    }

    private fun loadActivities() {
        scope.launch {
            repository.getAllActivities().collect { activities ->
                _uiState.update { it.copy(activities = activities) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { 
            it.copy(
                showAddDialog = true,
                newActivityName = "",
                newActivityArea = Area.ATENDIMENTO,
                newActivityFrequency = Frequency.DIARIO,
                newActivityEffort = 1,
                error = null
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun updateNewActivityName(name: String) {
        _uiState.update { it.copy(newActivityName = name, error = null) }
    }

    fun updateNewActivityArea(area: Area) {
        _uiState.update { it.copy(newActivityArea = area) }
    }

    fun updateNewActivityFrequency(frequency: Frequency) {
        _uiState.update { it.copy(newActivityFrequency = frequency) }
    }

    fun updateNewActivityEffort(effort: Int) {
        _uiState.update { it.copy(newActivityEffort = effort) }
    }

    fun addActivity() {
        val name = _uiState.value.newActivityName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Digite o nome da atividade") }
            return
        }

        repository.insertActivity(
            name = name,
            area = _uiState.value.newActivityArea,
            frequency = _uiState.value.newActivityFrequency,
            effort = _uiState.value.newActivityEffort
        )
        _uiState.update { 
            it.copy(
                showAddDialog = false,
                newActivityName = "",
                error = null
            )
        }
        loadActivities()
    }

    fun refresh() = loadActivities()

    fun deleteActivity(activity: Activity) {
        repository.deleteActivity(activity.id)
        loadActivities()
    }
}
