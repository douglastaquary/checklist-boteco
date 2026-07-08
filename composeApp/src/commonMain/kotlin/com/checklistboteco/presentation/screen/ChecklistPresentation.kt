package com.checklistboteco.presentation.screen

import com.checklistboteco.domain.model.ActivityWithCompletion

enum class ChecklistFilter(val label: String) { ALL("Todas"), PENDING("Pendentes"), COMPLETED("Concluídas") }

fun filterChecklistItems(items: List<ActivityWithCompletion>, filter: ChecklistFilter): List<ActivityWithCompletion> = when (filter) {
    ChecklistFilter.ALL -> items
    ChecklistFilter.PENDING -> items.filterNot { it.isCompleted }
    ChecklistFilter.COMPLETED -> items.filter { it.isCompleted }
}
