package com.checklistboteco.backend.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.time.DayOfWeek;

public final class Models {
    private Models() {}

    public enum Area { ATENDIMENTO, COZINHA, ESTOQUE, LIMPEZA }
    public enum PermissionLevel { ADMIN, USER }
    public enum Frequency { DIARIO, QUINZENAL, MENSAL }
    public enum ExecutionPhase { BEFORE_LUNCH, BEFORE_OPENING, DURING_OPERATION }
    public enum ChecklistTimingStatus { GREEN, YELLOW, RED, COMPLETED }
    public enum SyncStatus { SYNCED, PENDING, DELETED }
    public enum EntityType { ACTIVITY, COMPLETION }
    public enum SyncOperationType { ACTIVITY_UPSERT, ACTIVITY_DELETE, COMPLETION_CREATE }
    public enum SyncAckStatus { APPLIED, ALREADY_APPLIED, CONFLICT, REJECTED }
    public enum WorkSector {
        ATENDIMENTO(Area.ATENDIMENTO), COZINHA(Area.COZINHA), SERVICOS_GERAIS(Area.LIMPEZA),
        GARCON(Area.ATENDIMENTO), CUMIM(Area.ATENDIMENTO), CHEFE_DE_COZINHA(Area.COZINHA),
        GERENTE(Area.ATENDIMENTO), AJUDANTE_DE_COZINHA(Area.COZINHA), ATENDENTE(Area.ATENDIMENTO),
        BARMAN(Area.ATENDIMENTO);
        public final Area area;
        WorkSector(Area area) { this.area = area; }
    }

    public static class FeaturePermissions {
        public boolean canRegisterUsers;
        public boolean canCreateActivities;
        public boolean canEditUsers;
        public boolean canCreateInventoryCounts;
        public boolean canViewInventoryInsights;
        public boolean canManageAdministrativeStock;
        public boolean canImportPurchases;
        public FeaturePermissions() {}
        public FeaturePermissions(boolean register, boolean activities, boolean edit) {
            canRegisterUsers = register; canCreateActivities = activities; canEditUsers = edit;
        }
        public static FeaturePermissions admin() {
            var value=new FeaturePermissions(true,true,true);
            value.canCreateInventoryCounts=true;
            value.canViewInventoryInsights=true;
            value.canManageAdministrativeStock=true;
            value.canImportPurchases=true;
            return value;
        }
    }

    public static class User {
        public String id, name, email, passwordHash;
        public Area area;
        public WorkSector workSector;
        public PermissionLevel permissionLevel;
        public List<Area> allowedAreas = new ArrayList<>();
        public long createdAt, updatedAt;
        public SyncStatus syncStatus = SyncStatus.SYNCED;
        public FeaturePermissions permissions = new FeaturePermissions();
        public boolean mustChangePassword;
    }

    public static class PublicUser {
        public String id, name, email;
        public Area area;
        public WorkSector workSector;
        public PermissionLevel permissionLevel;
        public List<Area> allowedAreas = new ArrayList<>();
        public long createdAt, updatedAt;
        public SyncStatus syncStatus = SyncStatus.SYNCED;
        public FeaturePermissions permissions = new FeaturePermissions();
        public boolean mustChangePassword;
        public static PublicUser from(User user) {
            var result = new PublicUser();
            result.id=user.id; result.name=user.name; result.email=user.email; result.area=user.area;
            result.workSector=user.workSector; result.permissionLevel=user.permissionLevel;
            result.allowedAreas=new ArrayList<>(user.allowedAreas); result.createdAt=user.createdAt;
            result.updatedAt=user.updatedAt; result.syncStatus=user.syncStatus; result.permissions=user.permissions;
            result.mustChangePassword=user.mustChangePassword;
            return result;
        }
    }

    public static class Activity {
        public String id, name;
        public Area area;
        public Frequency frequency;
        public int effort = 1;
        public List<String> assigneeIds = new ArrayList<>();
        public int estimatedDurationMinutes = 15;
        public ExecutionPhase executionPhase = ExecutionPhase.BEFORE_LUNCH;
        public List<DayOfWeek> activeWeekdays = new ArrayList<>(List.of(DayOfWeek.TUESDAY,DayOfWeek.FRIDAY,DayOfWeek.SATURDAY,DayOfWeek.SUNDAY));
        public String recurrenceAnchorDate;
        public long createdAt, updatedAt, serverRevision, deletedAt;
        public SyncStatus syncStatus = SyncStatus.SYNCED;
    }

    public static class Completion {
        public String id, activityId, userId, imagePath;
        public long completedAt, createdAt, updatedAt, serverRevision;
        public boolean isLate;
        public String serviceDate;
        public SyncStatus syncStatus = SyncStatus.SYNCED;
    }

    public static class WorkClockEntry {
        public String id, userId, type;
        public long registeredAt, createdAt, updatedAt;
        public double latitude, longitude, distanceFromWorkMeters;
        public boolean isLate;
        public SyncStatus syncStatus = SyncStatus.SYNCED;
    }

    public static class Tombstone {
        public EntityType entityType;
        public String entityId;
        public long revision, deletedAt;
    }

    public static class SyncOperation {
        public String operationId, entityId;
        public SyncOperationType type;
        public long baseRevision, occurredAt;
        public Map<String,Object> payload = java.util.Map.of();
    }

    public static class SyncAcknowledgement {
        public String operationId;
        public SyncAckStatus status;
        public long serverRevision;
        public Activity conflict;
        public String message;
    }

    public static class DeviceChallenge {
        public String id, userId, deviceId, deviceName, code;
        public long expiresAt;
    }

    public static class LoginRequest { public String email, password, deviceId, deviceName; }
    public static class VerifyDeviceRequest { public String challengeId, code, deviceId, deviceName; }
    public static class LoginResponse {
        public String token;
        public PublicUser user;
        public boolean requiresTwoFactor;
        public String challengeId, deliveryHint, developmentCode;
    }
    public static class CreateUserRequest {
        public String name, email, password;
        public WorkSector workSector;
        public PermissionLevel permissionLevel = PermissionLevel.USER;
        public FeaturePermissions permissions = new FeaturePermissions();
    }
    public static class UpdateUserRequest {
        public String name, email;
        public WorkSector workSector;
        public PermissionLevel permissionLevel = PermissionLevel.USER;
    }
    public static class ResetPasswordRequest {
        public String newPassword;
    }
    public static class ChangePasswordRequest {
        public String currentPassword, newPassword;
    }
    public static class PermissionUpdateRequest { public FeaturePermissions permissions; }
    public static class CreateActivityRequest {
        public String name; public Area area; public Frequency frequency; public int effort=1;
        public List<String> assigneeIds = new ArrayList<>();
        public int estimatedDurationMinutes = 15;
        public ExecutionPhase executionPhase = ExecutionPhase.BEFORE_LUNCH;
        public List<DayOfWeek> activeWeekdays = new ArrayList<>();
        public String recurrenceAnchorDate;
    }
    public static class OperatingDaySchedule {
        public DayOfWeek dayOfWeek;
        public boolean active;
        public String entryTime, lunchTime, openingTime, closingTime;
        public String eventLabel;
    }
    public static class ChecklistSchedule {
        public String timezone = "America/Fortaleza";
        public Map<DayOfWeek,OperatingDaySchedule> days = new EnumMap<>(DayOfWeek.class);
        public static ChecklistSchedule defaults(){
            var value=new ChecklistSchedule();
            value.days.put(DayOfWeek.TUESDAY,day(DayOfWeek.TUESDAY,true,"15:00","17:00","18:00","00:00","Forró"));
            value.days.put(DayOfWeek.FRIDAY,day(DayOfWeek.FRIDAY,true,"15:00","17:00","18:00","00:00",null));
            value.days.put(DayOfWeek.SATURDAY,day(DayOfWeek.SATURDAY,true,"10:00","11:00","12:00","00:00",null));
            value.days.put(DayOfWeek.SUNDAY,day(DayOfWeek.SUNDAY,true,"10:00","11:00","12:00","00:00",null));
            for(var dow:DayOfWeek.values()) value.days.putIfAbsent(dow,day(dow,false,null,null,null,null,null));
            return value;
        }
        private static OperatingDaySchedule day(DayOfWeek dow,boolean active,String entry,String lunch,String opening,String closing,String label){
            var day=new OperatingDaySchedule(); day.dayOfWeek=dow; day.active=active; day.entryTime=entry; day.lunchTime=lunch; day.openingTime=opening; day.closingTime=closing; day.eventLabel=label; return day;
        }
    }
    public static class ChecklistOccurrence {
        public String occurrenceId, serviceDate, activityId, activityName;
        public Area area;
        public ExecutionPhase executionPhase;
        public int estimatedDurationMinutes;
        public List<String> assigneeIds = new ArrayList<>();
        public List<String> assigneeNames = new ArrayList<>();
        public long recommendedStartAt, deadlineAt;
        public ChecklistTimingStatus status;
        public Completion completion;
        public String completedByName;
    }
    public static class ChecklistOverview {
        public String serviceDate;
        public int green, yellow, red, completed, totalRemainingMinutes;
        public List<ChecklistOccurrence> occurrences = new ArrayList<>();
    }
    public static class DashboardStats {
        public int totalUsers, totalActivities, totalCompletions, pendingSyncItems;
        public Map<Area,Integer> activitiesByArea = new EnumMap<>(Area.class);
    }

    public static class SyncPushRequest {
        public String deviceId;
        public List<SyncOperation> operations = new ArrayList<>();
        public List<WorkClockEntry> workClockEntries = new ArrayList<>();
    }

    public static class SyncPushResult {
        public long serverTime;
        public String cursor = "0";
        public List<SyncAcknowledgement> acknowledgements = new ArrayList<>();
    }

    public static class SyncPullResponse {
        public long serverTime;
        public String nextCursor = "0";
        public boolean hasMore;
        public List<Activity> activities = new ArrayList<>();
        public List<Completion> completions = new ArrayList<>();
        public List<Tombstone> tombstones = new ArrayList<>();
        public ChecklistSchedule checklistSchedule;
    }

    public static class PullData {
        public String nextCursor = "0";
        public boolean hasMore;
        public List<Activity> activities = new ArrayList<>();
        public List<Completion> completions = new ArrayList<>();
        public List<Tombstone> tombstones = new ArrayList<>();
    }

    public static class ApiError {
        public String message;
        public ApiError() {}
        public ApiError(String message) { this.message=message; }
    }
}
