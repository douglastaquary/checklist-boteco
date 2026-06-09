package com.checklistboteco.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkClockCalculatorTest {
    @Test
    fun eightWorkedHoursAndOneBreakHourCompletesRegularDay() {
        val entries = listOf(
            entry(WorkClockType.ENTRADA, hour = 8),
            entry(WorkClockType.ALMOCO_INICIO, hour = 12),
            entry(WorkClockType.ALMOCO_FIM, hour = 13),
            entry(WorkClockType.SAIDA, hour = 17)
        )

        val summary = WorkClockCalculator.summarizeDay(entries)

        assertEquals(8.hours, summary.workedMillis)
        assertEquals(1.hours, summary.lunchMillis)
        assertEquals(0L, summary.missingDailyMillis)
        assertEquals(0L, summary.missingBreakMillis)
        assertEquals(0L, summary.breakOverageMillis)
    }

    @Test
    fun missingDailyHoursAreCalculatedFromWorkedTimeNotStartTime() {
        val entries = listOf(
            entry(WorkClockType.ENTRADA, hour = 11),
            entry(WorkClockType.SAIDA, hour = 17)
        )

        val summary = WorkClockCalculator.summarizeDay(entries)

        assertEquals(6.hours, summary.workedMillis)
        assertEquals(2.hours, summary.missingDailyMillis)
        assertEquals(1.hours, summary.missingBreakMillis)
    }

    @Test
    fun twelveHourShiftRequiresTwoBreakHours() {
        val entries = listOf(
            entry(WorkClockType.ENTRADA, hour = 6),
            entry(WorkClockType.ALMOCO_INICIO, hour = 12),
            entry(WorkClockType.ALMOCO_FIM, hour = 13),
            entry(WorkClockType.SAIDA, hour = 19)
        )

        val summary = WorkClockCalculator.summarizeDay(entries)

        assertEquals(12.hours, summary.workedMillis)
        assertTrue(summary.requiresTwoHoursRest)
        assertEquals(1.hours, summary.missingBreakMillis)
    }

    @Test
    fun entryIsNotLateWithoutFixedSchedule() {
        assertFalse(
            WorkClockCalculator.isLateEntry()
        )
    }

    private fun entry(type: WorkClockType, hour: Int): WorkClockEntry {
        return WorkClockEntry(
            id = hour.toLong(),
            userId = 1L,
            type = type,
            registeredAt = hour.hours,
            location = WorksiteLocation.point,
            distanceFromWorkMeters = 0.0,
            isLate = false
        )
    }

    private val Int.hours: Long
        get() = this * 60L * 60L * 1000L
}
