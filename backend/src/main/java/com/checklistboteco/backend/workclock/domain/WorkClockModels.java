package com.checklistboteco.backend.workclock.domain;

import java.util.ArrayList;
import java.util.List;

public final class WorkClockModels {
    private WorkClockModels() {}

    public static class UserWorkSchedule {
        public String userId;
        public List<Integer> workingDaysOfWeek = new ArrayList<>(List.of(1, 2, 3, 4));
        public List<String> workDateExceptions = new ArrayList<>();
        public List<String> offDateExceptions = new ArrayList<>();
    }

    public static class WorkClockSummaryRow {
        public String userId;
        public String name;
        public double workedHours;
        public double overtimeHours;
        public double missingHours;
        public double breakHours;
        public int absenceDays;
        public List<String> absenceDates = new ArrayList<>();
        public List<WorkClockAbsenceDetail> absenceDetails = new ArrayList<>();
    }

    public static class WorkClockMonthlyExportRow {
        public String userId;
        public String name;
        public double workedHours;
        public double overtimeHours;
        public double missingHours;
        public double lunchHours;
        public double restHours;
        public int absenceDays;
        public List<String> absenceDates = new ArrayList<>();
        public List<WorkClockAbsenceDetail> absenceDetails = new ArrayList<>();
    }

    public static class WorkClockAbsenceDetail {
        public String date;
        public String reason;
        public WorkClockAbsenceDetail() {}
        public WorkClockAbsenceDetail(String date, String reason) {
            this.date = date;
            this.reason = reason;
        }
    }

    public static class WorksiteInfo {
        public double latitude;
        public double longitude;
        public double radiusMeters;
        public String name;
    }
}
