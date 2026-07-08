package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.checklistboteco.domain.model.User
import com.checklistboteco.platform.CameraCaptureTrigger
import com.checklistboteco.presentation.designsystem.components.*
import com.checklistboteco.presentation.designsystem.tokens.BecoSpacing
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel

@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel,
    user: User,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val accessibleAreas = user.checklistAccessibleAreas
    var selectedFilter by remember { mutableStateOf(ChecklistFilter.ALL) }
    val visibleItems = remember(state.activities, selectedFilter) { filterChecklistItems(state.activities, selectedFilter) }

    CameraCaptureTrigger(
        trigger = state.showCameraForActivity != null,
        onImageCaptured = viewModel::onImageCaptured,
        onCancel = viewModel::onCameraCancel
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = BecoSpacing.md, vertical = BecoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BecoSpacing.sm)
        ) {
            item {
                BecoSegmentedFilter(
                    options = ChecklistFilter.entries.map { filter ->
                        BecoFilterOption(
                            value = filter,
                            label = filter.label,
                            count = when (filter) {
                                ChecklistFilter.ALL -> state.activities.size
                                ChecklistFilter.PENDING -> state.activities.count { !it.isCompleted }
                                ChecklistFilter.COMPLETED -> state.activities.count { it.isCompleted }
                            }
                        )
                    },
                    selected = selectedFilter,
                    onSelected = { selectedFilter = it }
                )
            }

            if (accessibleAreas.size > 1) {
                item {
                    BecoSegmentedFilter(
                        options = accessibleAreas.map { BecoFilterOption(it, it.displayName) },
                        selected = state.selectedArea,
                        onSelected = viewModel::selectArea
                    )
                }
            }

            if (visibleItems.isEmpty()) {
                item {
                    BecoEmptyState(
                        title = "Nenhuma atividade",
                        message = when (selectedFilter) {
                            ChecklistFilter.ALL -> "Não há atividades disponíveis para ${state.selectedArea.displayName}."
                            ChecklistFilter.PENDING -> "Todas as atividades visíveis foram concluídas."
                            ChecklistFilter.COMPLETED -> "Nenhuma atividade foi concluída ainda."
                        }
                    )
                }
            } else {
                item {
                    BecoTaskSection(title = state.selectedArea.displayName) {
                        visibleItems.forEachIndexed { index, item ->
                            BecoTaskRow(
                                title = item.activity.name,
                                metadata = "${item.activity.frequency.displayName} · esforço ${item.activity.effort}",
                                completed = item.isCompleted,
                                enabled = !item.isCompleted,
                                onClick = { viewModel.onActivityToggleClicked(item) }
                            )
                            if (index < visibleItems.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
