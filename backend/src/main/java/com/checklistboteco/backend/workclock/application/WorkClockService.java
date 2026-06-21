package com.checklistboteco.backend.workclock.application;

import com.checklistboteco.backend.model.Models.PublicUser;
import com.checklistboteco.backend.model.Models.WorkClockEntry;
import com.checklistboteco.backend.store.AppStore;
import com.checklistboteco.backend.workclock.domain.WorkClockCalculator;
import com.checklistboteco.backend.workclock.domain.WorkClockCalculator.DaySummary;
import com.checklistboteco.backend.workclock.domain.WorkClockModels;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.UserWorkSchedule;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.WorkClockMonthlyExportRow;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.WorkClockSummaryRow;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.WorksiteInfo;
import com.checklistboteco.backend.workclock.export.WorkClockCsvExporter;
import com.checklistboteco.backend.workclock.export.WorkClockPdfExporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WorkClockService {
    @Inject AppStore store;
    @Inject WorkClockPdfExporter pdfExporter;
    @Inject WorkClockCsvExporter csvExporter;

    @ConfigProperty(name = "worksite.latitude", defaultValue = "-23.85491") double worksiteLatitude;
    @ConfigProperty(name = "worksite.longitude", defaultValue = "-46.13872") double worksiteLongitude;
    @ConfigProperty(name = "worksite.radiusMeters", defaultValue = "5") double worksiteRadiusMeters;
    @ConfigProperty(name = "worksite.name", defaultValue = "Beco da Praia") String worksiteName;

    public List<WorkClockSummaryRow> summary(LocalDate from, LocalDate to, String userIdFilter) {
        List<PublicUser> users = filteredUsers(userIdFilter);
        List<WorkClockSummaryRow> rows = new ArrayList<>();
        for (PublicUser user : users) {
            List<WorkClockEntry> entries = store.listWorkClockEntries(user.id, from, to);
            UserWorkSchedule schedule = store.getWorkSchedule(user.id).orElse(defaultSchedule(user.id));
            rows.add(buildSummaryRow(user, entries, schedule, from, to));
        }
        return rows;
    }

    public List<WorkClockEntry> entries(String userId, LocalDate from, LocalDate to) {
        return store.listWorkClockEntries(userId, from, to);
    }

    public UserWorkSchedule getSchedule(String userId) {
        return store.getWorkSchedule(userId).orElse(defaultSchedule(userId));
    }

    public UserWorkSchedule saveSchedule(String userId, UserWorkSchedule schedule) {
        if (schedule == null) schedule = defaultSchedule(userId);
        schedule.userId = userId;
        if (schedule.workingDaysOfWeek == null || schedule.workingDaysOfWeek.isEmpty()) {
            schedule.workingDaysOfWeek = List.of(1, 2, 3, 4);
        }
        store.saveWorkSchedule(userId, schedule);
        return schedule;
    }

    public WorksiteInfo worksite() {
        WorksiteInfo info = new WorksiteInfo();
        info.latitude = worksiteLatitude;
        info.longitude = worksiteLongitude;
        info.radiusMeters = worksiteRadiusMeters;
        info.name = worksiteName;
        return info;
    }

    public byte[] exportCsv(int year, int month) {
        return csvExporter.export(monthlyRows(year, month));
    }

    public byte[] exportPdf(int year, int month) {
        return pdfExporter.export(year, month, monthlyRows(year, month));
    }

    private List<WorkClockMonthlyExportRow> monthlyRows(int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.with(TemporalAdjusters.lastDayOfMonth());
        List<WorkClockMonthlyExportRow> rows = new ArrayList<>();
        for (PublicUser user : store.users()) {
            List<WorkClockEntry> entries = store.listWorkClockEntries(user.id, from, to);
            UserWorkSchedule schedule = store.getWorkSchedule(user.id).orElse(defaultSchedule(user.id));
            WorkClockSummaryRow summary = buildSummaryRow(user, entries, schedule, from, to);
            WorkClockMonthlyExportRow row = new WorkClockMonthlyExportRow();
            row.userId = user.id;
            row.name = user.name;
            row.workedHours = summary.workedHours;
            row.overtimeHours = summary.overtimeHours;
            row.missingHours = summary.missingHours;
            row.absenceDays = summary.absenceDays;
            long lunch = 0L;
            long rest = 0L;
            for (List<WorkClockEntry> dayEntries : WorkClockCalculator.groupByDate(entries).values()) {
                DaySummary day = WorkClockCalculator.summarizeDay(dayEntries, 0L);
                lunch += day.lunchMillis();
                rest += day.restMillis();
            }
            row.lunchHours = WorkClockCalculator.toHours(lunch);
            row.restHours = WorkClockCalculator.toHours(rest);
            rows.add(row);
        }
        return rows;
    }

    private WorkClockSummaryRow buildSummaryRow(
        PublicUser user,
        List<WorkClockEntry> entries,
        UserWorkSchedule schedule,
        LocalDate from,
        LocalDate to
    ) {
        Map<LocalDate, List<WorkClockEntry>> byDate = WorkClockCalculator.groupByDate(entries);
        long worked = 0L;
        long breakMillis = 0L;
        for (List<WorkClockEntry> dayEntries : byDate.values()) {
            DaySummary day = WorkClockCalculator.summarizeDay(dayEntries, 0L);
            worked += day.workedMillis();
            breakMillis += day.lunchMillis() + day.restMillis();
        }
        long weeklyWorked = weeklyWorkedForRange(entries, from, to);
        WorkClockSummaryRow row = new WorkClockSummaryRow();
        row.userId = user.id;
        row.name = user.name;
        row.workedHours = WorkClockCalculator.toHours(worked);
        row.overtimeHours = WorkClockCalculator.toHours(WorkClockCalculator.overtimeMillis(weeklyWorked));
        row.missingHours = WorkClockCalculator.toHours(WorkClockCalculator.weeklyMissingMillis(weeklyWorked));
        row.breakHours = WorkClockCalculator.toHours(breakMillis);
        row.absenceDays = WorkClockCalculator.countAbsenceDays(entries, schedule, from, to);
        return row;
    }

    private long weeklyWorkedForRange(List<WorkClockEntry> entries, LocalDate from, LocalDate to) {
        LocalDate weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = to.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        long total = 0L;
        Map<LocalDate, List<WorkClockEntry>> byDate = WorkClockCalculator.groupByDate(entries);
        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            total += WorkClockCalculator.workedMillis(byDate.getOrDefault(date, List.of()));
        }
        return total;
    }

    private List<PublicUser> filteredUsers(String userIdFilter) {
        List<PublicUser> users = store.users();
        if (userIdFilter == null || userIdFilter.isBlank()) return users;
        return users.stream().filter(user -> userIdFilter.equals(user.id)).toList();
    }

    private UserWorkSchedule defaultSchedule(String userId) {
        UserWorkSchedule schedule = new UserWorkSchedule();
        schedule.userId = userId;
        schedule.workingDaysOfWeek = new ArrayList<>(List.of(1, 2, 3, 4));
        return schedule;
    }
}
