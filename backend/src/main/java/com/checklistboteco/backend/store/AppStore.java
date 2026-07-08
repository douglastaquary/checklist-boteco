package com.checklistboteco.backend.store;

import com.checklistboteco.backend.model.Models.*;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.UserWorkSchedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppStore {
    User authenticate(String email,String password);
    User getUser(String id);
    List<PublicUser> users();
    PublicUser createUser(CreateUserRequest request);
    PublicUser updateUser(String id,UpdateUserRequest request);
    void deleteUser(String id);
    PublicUser resetUserPassword(String id,String newPassword);
    PublicUser updatePermissions(String id,FeaturePermissions permissions);
    List<Activity> activities();
    Activity createActivity(CreateActivityRequest request);
    Activity updateActivity(String id,CreateActivityRequest request);
    List<Completion> completions();
    ChecklistSchedule checklistSchedule();
    ChecklistSchedule saveChecklistSchedule(ChecklistSchedule schedule);
    void upsertWorkClockEntries(List<WorkClockEntry> values);
    List<WorkClockEntry> listWorkClockEntries(String userId, LocalDate from, LocalDate to);
    Optional<UserWorkSchedule> getWorkSchedule(String userId);
    void saveWorkSchedule(String userId, UserWorkSchedule schedule);
    DashboardStats dashboard();
    PullData pullChanges(String userId,long cursor,int limit);
    SyncPushResult pushSync(String userId,boolean admin,SyncPushRequest request);
    boolean isTrustedDevice(String userId,String deviceId);
    DeviceChallenge createDeviceChallenge(String userId,String deviceId,String deviceName);
    User verifyDeviceChallenge(String challengeId,String code,String deviceId,String deviceName);
}
