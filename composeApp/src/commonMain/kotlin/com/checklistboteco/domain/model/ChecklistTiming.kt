package com.checklistboteco.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class OperatingDaySchedule(
    val dayOfWeek: String,
    val active: Boolean,
    val entryTime: String? = null,
    val lunchTime: String? = null,
    val openingTime: String? = null,
    val closingTime: String? = null,
    val eventLabel: String? = null
)

@Serializable
data class ChecklistSchedule(
    val timezone: String = "America/Fortaleza",
    val days: Map<String, OperatingDaySchedule> = defaultDays()
) {
    companion object {
        fun defaultDays() = mapOf(
            "TUESDAY" to OperatingDaySchedule("TUESDAY", true, "15:00", "17:00", "18:00", "00:00", "Forró"),
            "FRIDAY" to OperatingDaySchedule("FRIDAY", true, "15:00", "17:00", "18:00", "00:00"),
            "SATURDAY" to OperatingDaySchedule("SATURDAY", true, "10:00", "11:00", "12:00", "00:00"),
            "SUNDAY" to OperatingDaySchedule("SUNDAY", true, "10:00", "11:00", "12:00", "00:00")
        )
    }
}

data class ActivityTiming(
    val status: ChecklistTimingStatus,
    val deadlineAt: Long,
    val recommendedStartAt: Long
) {
    val statusLabel: String get() = when (status) {
        ChecklistTimingStatus.GREEN -> "Dentro do prazo"
        ChecklistTimingStatus.YELLOW -> "Próxima do limite"
        ChecklistTimingStatus.RED -> "Atrasada"
        ChecklistTimingStatus.COMPLETED -> "Concluída"
    }
}

object ChecklistTiming {
    private val timezone = TimeZone.of("America/Fortaleza")

    fun forToday(activity: Activity, completion: ActivityCompletion?, now: Long = Clock.System.now().toEpochMilliseconds(), schedule: ChecklistSchedule = ChecklistSchedule()): ActivityTiming {
        val resolvedTimezone = TimeZone.of(schedule.timezone)
        val local = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(resolvedTimezone)
        val weekday = local.date.dayOfWeek.name
        val day = schedule.days[weekday]
        val deadlineValue = when (activity.executionPhase) {
            ExecutionPhase.BEFORE_LUNCH -> day?.lunchTime
            ExecutionPhase.BEFORE_OPENING -> day?.openingTime
            ExecutionPhase.DURING_OPERATION -> day?.closingTime
        } ?: "23:59"
        val parts = deadlineValue.split(":")
        val deadlineTime = LocalTime(parts[0].toInt(), parts.getOrElse(1) { "0" }.toInt())
        val deadline = LocalDateTime(local.date, deadlineTime).toInstant(resolvedTimezone).toEpochMilliseconds() +
            if (activity.executionPhase == ExecutionPhase.DURING_OPERATION) 86_400_000L else 0L
        val status = when {
            completion != null -> ChecklistTimingStatus.COMPLETED
            now > deadline -> ChecklistTimingStatus.RED
            deadline - now <= 30 * 60 * 1000L -> ChecklistTimingStatus.YELLOW
            else -> ChecklistTimingStatus.GREEN
        }
        return ActivityTiming(status, deadline, deadline - activity.estimatedDurationMinutes * 60_000L)
    }

    fun isDueToday(activity: Activity, now: Long = Clock.System.now().toEpochMilliseconds(), schedule: ChecklistSchedule = ChecklistSchedule()): Boolean {
        val date = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.of(schedule.timezone)).date
        if (date.dayOfWeek.name !in activity.activeWeekdays) return false
        if (activity.frequency == Frequency.DIARIO) return true
        val anchor = runCatching { activity.recurrenceAnchorDate?.let(LocalDate::parse) }.getOrNull() ?: date
        if (date < anchor) return false
        return when (activity.frequency) {
            Frequency.DIARIO -> true
            Frequency.QUINZENAL -> (date.toEpochDays() - anchor.toEpochDays()) % 14 == 0
            Frequency.MENSAL -> date.dayOfMonth == anchor.dayOfMonth
        }
    }
}
