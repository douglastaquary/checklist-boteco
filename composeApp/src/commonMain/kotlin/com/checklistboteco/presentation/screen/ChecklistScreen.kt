package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.ChecklistTiming
import com.checklistboteco.domain.model.ChecklistTimingStatus
import com.checklistboteco.platform.CameraCaptureTrigger
import com.checklistboteco.platform.ChecklistNotificationEffect
import com.checklistboteco.presentation.designsystem.components.*
import com.checklistboteco.presentation.designsystem.tokens.BecoSpacing
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    val now by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) { delay(60_000); value = Clock.System.now().toEpochMilliseconds() }
    }
    val weekday = remember(now, state.schedule) { kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.of(state.schedule.timezone)).date.dayOfWeek.name }
    val scheduledItems = remember(visibleItems, weekday, state.schedule, now) { if (state.schedule.days[weekday]?.active == true) visibleItems.filter { ChecklistTiming.isDueToday(it.activity, now, state.schedule) } else emptyList() }
    val pendingTiming = remember(scheduledItems, now, state.schedule) { scheduledItems.filter { !it.isCompleted }.map { ChecklistTiming.forToday(it.activity, it.completion, now, state.schedule) } }

    CameraCaptureTrigger(
        trigger = state.showCameraForActivity != null,
        onImageCaptured = viewModel::onImageCaptured,
        onCancel = viewModel::onCameraCancel
    )
    ChecklistNotificationEffect(scheduledItems, user.remoteId, state.schedule)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = BecoSpacing.md, vertical = BecoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BecoSpacing.sm)
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(BecoSpacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${pendingTiming.size} pendentes")
                        Text("${pendingTiming.count { it.status == ChecklistTimingStatus.RED }} atrasadas")
                        Text("${scheduledItems.filter { !it.isCompleted }.sumOf { it.activity.estimatedDurationMinutes }} min restantes")
                    }
                }
            }
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

            if (scheduledItems.isEmpty()) {
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
                        scheduledItems.forEachIndexed { index, item ->
                            val timing = ChecklistTiming.forToday(item.activity, item.completion, now, state.schedule)
                            BecoTaskRow(
                                title = item.activity.name,
                                metadata = "${item.activity.executionPhase.displayName} · ${item.activity.estimatedDurationMinutes} min · ${timing.statusLabel}",
                                completed = item.isCompleted,
                                enabled = !item.isCompleted,
                                timingStatus = timing.status,
                                onClick = { viewModel.onActivityToggleClicked(item) }
                            )
                            if (index < scheduledItems.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
