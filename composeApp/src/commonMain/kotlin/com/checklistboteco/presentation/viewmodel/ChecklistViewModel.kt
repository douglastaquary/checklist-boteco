package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.User
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
    val pendingConfirmationActivity: Long? = null
)

class ChecklistViewModel(
    private val repository: ChecklistRepository,
    private val currentUser: User?,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(ChecklistUiState(currentUser = currentUser))
    val uiState: StateFlow<ChecklistUiState> = _uiState.asStateFlow()

    init {
        repository.seedInitialData()
        loadActivities()
    }

    private fun loadActivities() {
        scope.launch {
            repository.getActivitiesWithCompletion(_uiState.value.selectedArea).collect { activities ->
                _uiState.update { it.copy(activities = activities) }
            }
        }
    }

    fun selectArea(area: Area) {
        if (currentUser?.canAccessArea(area) != true) return
        _uiState.update { it.copy(selectedArea = area) }
        scope.launch {
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
