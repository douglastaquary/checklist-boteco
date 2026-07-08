package com.checklistboteco.domain.model

data class Activity(
    val id: Long,
    val syncId: String,
    val name: String,
    val area: Area,
    val frequency: Frequency,
    val effort: Int = 1,
    val assigneeIds: List<String> = emptyList(),
    val estimatedDurationMinutes: Int = 15,
    val executionPhase: ExecutionPhase = ExecutionPhase.BEFORE_LUNCH,
    val activeWeekdays: List<String> = listOf("TUESDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
    val recurrenceAnchorDate: String? = null,
    val serverRevision: Long = 0L,
    val syncState: SyncState = SyncState.SYNCED,
    val deletedAt: Long? = null
)

enum class ExecutionPhase(val displayName: String) {
    BEFORE_LUNCH("Antes do almoço"),
    BEFORE_OPENING("Antes da abertura"),
    DURING_OPERATION("Durante a operação")
}

enum class ChecklistTimingStatus { GREEN, YELLOW, RED, COMPLETED }
