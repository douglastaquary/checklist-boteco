package com.checklistboteco.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ChecklistTimingTest {
    @Test fun tuesdayTurnsYellowThirtyMinutesBeforeLunch() {
        val activity = Activity(1, "a1", "Preparar salão", Area.ATENDIMENTO, Frequency.DIARIO, estimatedDurationMinutes = 45)
        val now = LocalDateTime(2026, 7, 7, 16, 40).toInstant(TimeZone.of("America/Fortaleza")).toEpochMilliseconds()
        assertEquals(ChecklistTimingStatus.YELLOW, ChecklistTiming.forToday(activity, null, now).status)
    }

    @Test fun completedTaskHasCompletedStatus() {
        val activity = Activity(1, "a1", "Preparar salão", Area.ATENDIMENTO, Frequency.DIARIO)
        val completion = ActivityCompletion(1, "c1", 1, 1, 1, null)
        assertEquals(ChecklistTimingStatus.COMPLETED, ChecklistTiming.forToday(activity, completion).status)
    }
}
