package com.checklistboteco.backend.workclock.domain;

import com.checklistboteco.backend.model.Models.WorkClockEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkClockCalculator {
    public static final long DAILY_EXPECTED_MILLIS = 8L * 60L * 60L * 1000L;
    public static final long WEEKLY_EXPECTED_MILLIS = 40L * 60L * 60L * 1000L;
    public static final long REGULAR_BREAK_MILLIS = 60L * 60L * 1000L;
    public static final long EXTENDED_SHIFT_BREAK_MILLIS = 2L * 60L * 60L * 1000L;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private WorkClockCalculator() {}

    public enum WorkClockType {
        ENTRADA, ALMOCO_INICIO, ALMOCO_FIM, DESCANSO_INICIO, DESCANSO_FIM, SAIDA
    }

    public record DaySummary(
        long workedMillis,
        long lunchMillis,
        long restMillis,
        long requiredBreakMillis,
        long missingBreakMillis,
        long breakOverageMillis,
        long missingDailyMillis,
        long missingWeeklyMillis,
        boolean requiresTwoHoursRest
    ) {}

    public static DaySummary summarizeDay(List<WorkClockEntry> entries, long weeklyWorkedMillis) {
        long worked = workedMillis(entries);
        long lunch = durationBetween(entries, WorkClockType.ALMOCO_INICIO, WorkClockType.ALMOCO_FIM);
        long rest = durationBetween(entries, WorkClockType.DESCANSO_INICIO, WorkClockType.DESCANSO_FIM);
        long breakMillis = lunch + rest;
        long requiredBreak = worked >= 12L * 60L * 60L * 1000L ? EXTENDED_SHIFT_BREAK_MILLIS : REGULAR_BREAK_MILLIS;
        boolean empty = entries == null || entries.isEmpty();
        return new DaySummary(
            worked,
            lunch,
            rest,
            requiredBreak,
            empty ? 0L : Math.max(0L, requiredBreak - breakMillis),
            empty ? 0L : Math.max(0L, breakMillis - requiredBreak),
            empty ? 0L : Math.max(0L, DAILY_EXPECTED_MILLIS - worked),
            Math.max(0L, WEEKLY_EXPECTED_MILLIS - weeklyWorkedMillis),
            worked >= 12L * 60L * 60L * 1000L && breakMillis < EXTENDED_SHIFT_BREAK_MILLIS
        );
    }

    public static long workedMillis(List<WorkClockEntry> entries) {
        return durationAcross(
            sorted(entries),
            EnumSet.of(WorkClockType.ENTRADA, WorkClockType.ALMOCO_FIM, WorkClockType.DESCANSO_FIM),
            EnumSet.of(WorkClockType.ALMOCO_INICIO, WorkClockType.DESCANSO_INICIO, WorkClockType.SAIDA)
        );
    }

    public static long overtimeMillis(long weeklyWorkedMillis) {
        return Math.max(0L, weeklyWorkedMillis - WEEKLY_EXPECTED_MILLIS);
    }

    public static long weeklyMissingMillis(long weeklyWorkedMillis) {
        return Math.max(0L, WEEKLY_EXPECTED_MILLIS - weeklyWorkedMillis);
    }

    public static boolean isScheduledWorkDay(LocalDate date, WorkClockModels.UserWorkSchedule schedule) {
        String iso = date.toString();
        if (schedule != null && schedule.offDateExceptions != null && schedule.offDateExceptions.contains(iso)) {
            return false;
        }
        if (schedule != null && schedule.workDateExceptions != null && schedule.workDateExceptions.contains(iso)) {
            return true;
        }
        List<Integer> days = schedule == null || schedule.workingDaysOfWeek == null
            ? List.of(1, 2, 3, 4)
            : schedule.workingDaysOfWeek;
        return days.contains(date.getDayOfWeek().getValue());
    }

    public static int countAbsenceDays(
        List<WorkClockEntry> entries,
        WorkClockModels.UserWorkSchedule schedule,
        LocalDate from,
        LocalDate to
    ) {
        Map<LocalDate, List<WorkClockEntry>> byDate = groupByDate(entries);
        int absences = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!isScheduledWorkDay(date, schedule)) continue;
            List<WorkClockEntry> dayEntries = byDate.getOrDefault(date, List.of());
            boolean hasEntrada = dayEntries.stream().anyMatch(e -> WorkClockType.ENTRADA.name().equals(e.type));
            if (!hasEntrada) {
                absences++;
                continue;
            }
            boolean hasSaida = dayEntries.stream().anyMatch(e -> WorkClockType.SAIDA.name().equals(e.type));
            if (!hasSaida && date.isBefore(LocalDate.now(ZONE))) {
                absences++;
            }
        }
        return absences;
    }

    public static Map<LocalDate, List<WorkClockEntry>> groupByDate(List<WorkClockEntry> entries) {
        Map<LocalDate, List<WorkClockEntry>> grouped = new HashMap<>();
        if (entries == null) return grouped;
        for (WorkClockEntry entry : entries) {
            LocalDate date = toLocalDate(entry.registeredAt);
            grouped.computeIfAbsent(date, ignored -> new ArrayList<>()).add(entry);
        }
        grouped.values().forEach(list -> list.sort(Comparator.comparingLong(e -> e.registeredAt)));
        return grouped;
    }

    public static LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDate();
    }

    public static double toHours(long millis) {
        return millis / 3_600_000.0;
    }

    public static String formatDuration(long millis) {
        long totalMinutes = millis / 60_000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "h " + String.format("%02d", minutes) + "min";
    }

    private static long durationBetween(List<WorkClockEntry> entries, WorkClockType start, WorkClockType stop) {
        return durationAcross(sorted(entries), EnumSet.of(start), EnumSet.of(stop));
    }

    private static long durationAcross(List<WorkClockEntry> entries, Set<WorkClockType> startTypes, Set<WorkClockType> stopTypes) {
        Long currentStart = null;
        long total = 0L;
        for (WorkClockEntry entry : entries) {
            WorkClockType type = parseType(entry.type);
            if (startTypes.contains(type)) currentStart = entry.registeredAt;
            if (stopTypes.contains(type) && currentStart != null) {
                total += Math.max(0L, entry.registeredAt - currentStart);
                currentStart = null;
            }
        }
        return total;
    }

    private static List<WorkClockEntry> sorted(List<WorkClockEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        return entries.stream().sorted(Comparator.comparingLong(e -> e.registeredAt)).toList();
    }

    private static WorkClockType parseType(String value) {
        try {
            return WorkClockType.valueOf(value);
        } catch (Exception error) {
            return WorkClockType.ENTRADA;
        }
    }
}
