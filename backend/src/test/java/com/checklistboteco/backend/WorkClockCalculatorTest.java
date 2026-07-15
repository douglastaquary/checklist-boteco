package com.checklistboteco.backend;

import com.checklistboteco.backend.model.Models.WorkClockEntry;
import com.checklistboteco.backend.workclock.domain.WorkClockCalculator;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.UserWorkSchedule;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkClockCalculatorTest {
    @Test
    void eightWorkedHoursAndOneBreakHourCompletesRegularDay() {
        List<WorkClockEntry> entries = List.of(
            entry("ENTRADA", 8),
            entry("ALMOCO_INICIO", 12),
            entry("ALMOCO_FIM", 13),
            entry("SAIDA", 17)
        );
        var summary = WorkClockCalculator.summarizeDay(entries, 0L);
        assertEquals(8L * 60 * 60 * 1000, summary.workedMillis());
        assertEquals(1L * 60 * 60 * 1000, summary.lunchMillis());
        assertEquals(0L, summary.missingDailyMillis());
        assertEquals(0L, summary.missingBreakMillis());
    }

    @Test
    void twelveHourShiftRequiresTwoBreakHours() {
        List<WorkClockEntry> entries = List.of(
            entry("ENTRADA", 6),
            entry("ALMOCO_INICIO", 12),
            entry("ALMOCO_FIM", 13),
            entry("SAIDA", 19)
        );
        var summary = WorkClockCalculator.summarizeDay(entries, 0L);
        assertEquals(12L * 60 * 60 * 1000, summary.workedMillis());
        assertTrue(summary.requiresTwoHoursRest());
        assertEquals(1L * 60 * 60 * 1000, summary.missingBreakMillis());
    }

    @Test
    void overtimeIsOnlyAboveFortyWeeklyHours() {
        long fortyHours = 40L * 60 * 60 * 1000;
        assertEquals(0L, WorkClockCalculator.overtimeMillis(fortyHours));
        assertEquals(1L * 60 * 60 * 1000, WorkClockCalculator.overtimeMillis(fortyHours + 3_600_000L));
    }

    @Test
    void absenceCountsScheduledDayWithoutEntrada() {
        UserWorkSchedule schedule = new UserWorkSchedule();
        schedule.workingDaysOfWeek = List.of(1, 2, 3, 4, 5);
        LocalDate monday = LocalDate.of(2026, 6, 15);
        int absences = WorkClockCalculator.countAbsenceDays(List.of(), schedule, monday, monday);
        assertEquals(1, absences);
    }

    @Test
    void absenceDetailsListMissingEntryAndMissingExitForPastDays() {
        UserWorkSchedule schedule = new UserWorkSchedule();
        schedule.workingDaysOfWeek = List.of(1, 2, 3, 4, 5, 6);
        LocalDate friday = LocalDate.of(2026, 6, 19);
        LocalDate saturday = LocalDate.of(2026, 6, 20);
        WorkClockEntry saturdayEntry = entry("ENTRADA", saturday, 8);

        var absences = WorkClockCalculator.absenceDetails(List.of(saturdayEntry), schedule, friday, saturday);

        assertEquals(2, absences.size());
        assertEquals("2026-06-19", absences.get(0).date);
        assertEquals("Sem entrada registrada", absences.get(0).reason);
        assertEquals("2026-06-20", absences.get(1).date);
        assertEquals("Entrada sem saída registrada", absences.get(1).reason);
    }

    private WorkClockEntry entry(String type, int hour) {
        return entry(type, LocalDate.of(2026, 6, 20), hour);
    }

    private WorkClockEntry entry(String type, LocalDate date, int hour) {
        WorkClockEntry entry = new WorkClockEntry();
        entry.type = type;
        entry.registeredAt = date.atTime(hour, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();
        return entry;
    }
}
