package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.data.sync.SyncCoordinator
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.ChecklistSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChecklistUiState(
    val activities: List<ActivityWithCompletion> = emptyList(),
    val selectedArea: Area = Area.ATENDIMENTO,
    val isLoading: Boolean = false,
    val currentUser: User? = null,
    val showCameraForActivity: Long? = null,
    val pendingConfirmationActivity: Long? = null,
    val schedule: ChecklistSchedule = ChecklistSchedule()
)

class ChecklistViewModel(
    private val repository: ChecklistRepository,
    private val currentUser: User?,
    private val syncCoordinator: SyncCoordinator?,
    private val scope: CoroutineScope
) {
    private val initialArea = currentUser?.checklistAccessibleAreas?.firstOrNull() ?: Area.ATENDIMENTO
    private val _uiState = MutableStateFlow(
        ChecklistUiState(
            selectedArea = initialArea,
            currentUser = currentUser
        )
    )
    val uiState: StateFlow<ChecklistUiState> = _uiState.asStateFlow()

    init {
        loadActivities()
    }

    private fun loadActivities() {
        scope.launch {
            syncCoordinator?.syncOnce()
            _uiState.update { it.copy(schedule = repository.getChecklistSchedule()) }
            repository.getActivitiesWithCompletion(_uiState.value.selectedArea).collect { activities ->
                _uiState.update { it.copy(activities = activities) }
            }
        }
    }

    fun selectArea(area: Area) {
        if (currentUser?.canAccessChecklistArea(area) != true) return
        _uiState.update { it.copy(selectedArea = area) }
        scope.launch {
            syncCoordinator?.syncOnce()
            repository.getActivitiesWithCompletion(area).collect { activities ->
                _uiState.update { it.copy(activities = activities) }
            }
        }
    }

    fun onActivityToggleClicked(activityWithCompletion: ActivityWithCompletion) {
        if (activityWithCompletion.isCompleted) return // Já concluída, não faz nada
        _uiState.update { 
            it.copy(
                showCameraForActivity = activityWithCompletion.activity.id,
                pendingConfirmationActivity = activityWithCompletion.activity.id
            )
        }
    }

    fun onImageCaptured(imagePath: String) {
        val activityId = _uiState.value.pendingConfirmationActivity ?: return
        val user = currentUser ?: return
        
        // Determinar se está atrasado (simplificado: se passar do meio do dia para diários)
        val isLate = false // Lógica de atraso pode ser refinada aqui
        
        repository.insertCompletion(activityId, user.id, imagePath, isLate)
        syncCoordinator?.requestSync()
        _uiState.update { 
            it.copy(
                showCameraForActivity = null,
                pendingConfirmationActivity = null
            )
        }
        loadActivities()
    }

    fun onCameraCancel() {
        _uiState.update { 
            it.copy(
                showCameraForActivity = null,
                pendingConfirmationActivity = null
            )
        }
    }
}
