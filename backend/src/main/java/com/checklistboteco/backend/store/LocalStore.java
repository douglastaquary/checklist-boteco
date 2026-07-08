package com.checklistboteco.backend.store;

import com.checklistboteco.backend.model.Models.*;
import com.checklistboteco.backend.security.PasswordHasher;
import com.checklistboteco.backend.workclock.domain.WorkClockModels.UserWorkSchedule;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@UnlessBuildProfile("prod")
public class LocalStore implements AppStore {
    protected final Map<String,User> users=new ConcurrentHashMap<>();
    protected final Map<String,Activity> activities=new ConcurrentHashMap<>();
    protected final Map<String,Completion> completions=new ConcurrentHashMap<>();
    protected final Map<String,WorkClockEntry> workClock=new ConcurrentHashMap<>();
    protected final Map<String,UserWorkSchedule> userSchedules=new ConcurrentHashMap<>();
    protected final Map<String,Tombstone> tombstones=new ConcurrentHashMap<>();
    protected final Map<String,SyncAcknowledgement> processedOperations=new ConcurrentHashMap<>();
    protected final List<ChangeRecord> changeLog=new ArrayList<>();
    protected final Map<String,DeviceChallenge> challenges=new ConcurrentHashMap<>();
    protected final java.util.Set<String> trustedDevices=ConcurrentHashMap.newKeySet();
    private final PasswordHasher passwords=new PasswordHasher();
    private final SecureRandom random=new SecureRandom();
    private long cursorSequence=0L;
    @ConfigProperty(name="checklist.initial-admin-password", defaultValue="admin123") String initialAdminPassword;

    @PostConstruct void initializeLocal(){ if(!(this instanceof DynamoDbStore)) seed(); }

    protected void seed() {
        if(!users.isEmpty()) return;
        var request=new CreateUserRequest(); request.name="Administrador"; request.email="admin@checklistboteco.com";
        request.password=initialAdminPassword; request.workSector=WorkSector.GERENTE; request.permissionLevel=PermissionLevel.ADMIN;
        createUser(request);
        createSeedActivity("Abrir o salão",Area.ATENDIMENTO,Frequency.DIARIO,2);
        createSeedActivity("Conferir estoque crítico",Area.ESTOQUE,Frequency.DIARIO,3);
        createSeedActivity("Higienizar bancadas",Area.COZINHA,Frequency.DIARIO,2);
    }

    private void createSeedActivity(String name,Area area,Frequency frequency,int effort) {
        var request=new CreateActivityRequest();
        request.name=name; request.area=area; request.frequency=frequency; request.effort=effort;
        createActivity(request);
    }

    public User authenticate(String email,String password) {
        if(email==null||password==null) return null;
        return users.values().stream()
            .filter(u->u.email.equalsIgnoreCase(email.trim())&&passwords.verify(password,u.passwordHash))
            .findFirst().orElse(null);
    }

    public User getUser(String id){ return users.get(id); }

    public List<PublicUser> users() {
        return users.values().stream()
            .sorted(Comparator.comparing(u->u.name))
            .map(PublicUser::from)
            .toList();
    }

    public synchronized PublicUser createUser(CreateUserRequest request) {
        require(request!=null&&request.name!=null&&!request.name.isBlank(),"Nome é obrigatório");
        require(request.email!=null&&request.email.contains("@"),"Email inválido");
        require(request.password!=null&&request.password.length()>=8,"Senha deve ter ao menos 8 caracteres");
        require(request.workSector!=null,"Setor é obrigatório");
        require(users.values().stream().noneMatch(u->u.email.equalsIgnoreCase(request.email.trim())),"Usuário já existe");
        long now=System.currentTimeMillis();
        var user=new User();
        user.id=UUID.randomUUID().toString();
        user.name=request.name.trim();
        user.email=request.email.trim().toLowerCase(Locale.ROOT);
        user.passwordHash=passwords.hash(request.password);
        user.workSector=request.workSector;
        user.area=request.workSector.area;
        user.permissionLevel=request.permissionLevel==null?PermissionLevel.USER:request.permissionLevel;
        user.allowedAreas=user.permissionLevel==PermissionLevel.ADMIN?List.of(Area.values()):List.of(user.area);
        user.createdAt=now;
        user.updatedAt=now;
        user.permissions=user.permissionLevel==PermissionLevel.ADMIN?FeaturePermissions.admin():nonNull(request.permissions);
        users.put(user.id,user);
        return PublicUser.from(user);
    }

    public synchronized PublicUser updateUser(String id,UpdateUserRequest request) {
        User user=users.get(id);
        require(user!=null,"Usuário não encontrado");
        require(request!=null,"Dados do usuário são obrigatórios");
        require(request.name!=null&&!request.name.isBlank(),"Nome é obrigatório");
        require(request.email!=null&&request.email.contains("@"),"Email inválido");
        require(request.workSector!=null,"Setor é obrigatório");
        PermissionLevel previousLevel=user.permissionLevel;
        String normalizedEmail=request.email.trim().toLowerCase(Locale.ROOT);
        require(users.values().stream().noneMatch(value->!value.id.equals(id)&&value.email.equalsIgnoreCase(normalizedEmail)),"Já existe outro usuário com este email");
        user.name=request.name.trim();
        user.email=normalizedEmail;
        user.workSector=request.workSector;
        user.area=request.workSector.area;
        user.permissionLevel=request.permissionLevel==null?PermissionLevel.USER:request.permissionLevel;
        user.allowedAreas=user.permissionLevel==PermissionLevel.ADMIN?List.of(Area.values()):List.of(user.area);
        if(user.permissionLevel==PermissionLevel.ADMIN) user.permissions=FeaturePermissions.admin();
        else if(previousLevel==PermissionLevel.ADMIN) user.permissions=new FeaturePermissions();
        user.updatedAt=System.currentTimeMillis();
        return PublicUser.from(user);
    }

    public synchronized void deleteUser(String id) {
        User user=users.get(id);
        require(user!=null,"Usuário não encontrado");
        if(user.permissionLevel==PermissionLevel.ADMIN){
            long admins=users.values().stream().filter(value->value.permissionLevel==PermissionLevel.ADMIN).count();
            require(admins>1,"Não é possível remover o último administrador");
        }
        users.remove(id);
        trustedDevices.removeIf(value->value.startsWith(id+":"));
        challenges.values().removeIf(value->Objects.equals(value.userId,id));
        workClock.values().removeIf(value->Objects.equals(value.userId,id));
        userSchedules.remove(id);
        completions.values().removeIf(value->Objects.equals(value.userId,id));
    }

    public synchronized PublicUser resetUserPassword(String id,String newPassword) {
        User user=users.get(id);
        require(user!=null,"Usuário não encontrado");
        require(newPassword!=null&&newPassword.length()>=8,"Senha deve ter ao menos 8 caracteres");
        user.passwordHash=passwords.hash(newPassword);
        user.updatedAt=System.currentTimeMillis();
        trustedDevices.removeIf(value->value.startsWith(id+":"));
        challenges.values().removeIf(value->Objects.equals(value.userId,id));
        return PublicUser.from(user);
    }

    public synchronized PublicUser updatePermissions(String id,FeaturePermissions permissions) {
        User user=users.get(id);
        require(user!=null,"Usuário não encontrado");
        user.permissions=user.permissionLevel==PermissionLevel.ADMIN?FeaturePermissions.admin():nonNull(permissions);
        user.updatedAt=System.currentTimeMillis();
        return PublicUser.from(user);
    }

    public List<Activity> activities() {
        return activities.values().stream()
            .filter(a->a.syncStatus!=SyncStatus.DELETED)
            .sorted(Comparator.comparing(a->a.name))
            .toList();
    }

    public synchronized Activity createActivity(CreateActivityRequest request) {
        require(request!=null&&request.name!=null&&!request.name.isBlank(),"Nome da atividade é obrigatório");
        require(request.area!=null&&request.frequency!=null,"Área e frequência são obrigatórias");
        long now=System.currentTimeMillis();
        var activity=new Activity();
        activity.id=UUID.randomUUID().toString();
        activity.name=request.name.trim();
        activity.area=request.area;
        activity.frequency=request.frequency;
        activity.effort=Math.max(1,Math.min(5,request.effort));
        activity.createdAt=now;
        activity.updatedAt=now;
        activity.serverRevision=1L;
        activities.put(activity.id,activity);
        recordChange(EntityType.ACTIVITY,activity.id);
        return activity;
    }

    public List<Completion> completions() {
        return completions.values().stream()
            .sorted(Comparator.comparingLong((Completion c)->c.completedAt).reversed())
            .toList();
    }

    public void upsertWorkClockEntries(List<WorkClockEntry> values) {
        safe(values).forEach(v->workClock.put(v.id,v));
    }

    public List<WorkClockEntry> listWorkClockEntries(String userId, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start : to;
        long fromMillis = start.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();
        long toMillis = end.plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();
        return workClock.values().stream()
            .filter(entry -> userId == null || userId.isBlank() || Objects.equals(entry.userId, userId))
            .filter(entry -> entry.registeredAt >= fromMillis && entry.registeredAt < toMillis)
            .sorted(Comparator.comparingLong(entry -> entry.registeredAt))
            .toList();
    }

    public Optional<UserWorkSchedule> getWorkSchedule(String userId) {
        return Optional.ofNullable(userSchedules.get(userId));
    }

    public void saveWorkSchedule(String userId, UserWorkSchedule schedule) {
        if (userId == null || userId.isBlank() || schedule == null) return;
        schedule.userId = userId;
        userSchedules.put(userId, schedule);
    }

    public DashboardStats dashboard() {
        var result=new DashboardStats();
        result.totalUsers=users.size();
        result.totalActivities=(int)activities.values().stream().filter(a->a.syncStatus!=SyncStatus.DELETED).count();
        result.totalCompletions=completions.size();
        activities().forEach(a->result.activitiesByArea.merge(a.area,1,Integer::sum));
        return result;
    }

    public synchronized PullData pullChanges(String userId,long cursor,int limit) {
        var data=new PullData();
        List<ChangeRecord> page=changeLog.stream().filter(change->change.cursor>cursor).limit(limit).toList();
        for(ChangeRecord change:page){
            if(change.entityType==EntityType.ACTIVITY){
                Tombstone tombstone=tombstones.get(change.entityId);
                if(tombstone!=null){
                    data.tombstones.add(copy(tombstone));
                }else{
                    Activity activity=activities.get(change.entityId);
                    if(activity!=null&&activity.syncStatus!=SyncStatus.DELETED){
                        data.activities.add(copy(activity));
                    }
                }
            }else if(change.entityType==EntityType.COMPLETION){
                Completion completion=completions.get(change.entityId);
                if(completion!=null&&Objects.equals(completion.userId,userId)){
                    data.completions.add(copy(completion));
                }
            }
        }
        data.hasMore=page.size()==limit&&changeLog.stream().anyMatch(change->change.cursor>page.get(page.size()-1).cursor);
        data.nextCursor=page.isEmpty()?Long.toString(cursor):Long.toString(page.get(page.size()-1).cursor);
        return data;
    }

    public synchronized SyncPushResult pushSync(String userId,boolean admin,SyncPushRequest request) {
        var result=new SyncPushResult();
        result.serverTime=System.currentTimeMillis();
        for(SyncOperation operation:safe(request==null?null:request.operations)){
            result.acknowledgements.add(applyOperation(userId,admin,operation));
        }
        upsertWorkClockEntries(request==null?List.of():request.workClockEntries);
        result.cursor=Long.toString(cursorSequence);
        return result;
    }

    public boolean isTrustedDevice(String userId,String deviceId){ return trustedDevices.contains(userId+":"+deviceId); }

    public DeviceChallenge createDeviceChallenge(String userId,String deviceId,String deviceName){
        var value=new DeviceChallenge();
        value.id=UUID.randomUUID().toString();
        value.userId=userId;
        value.deviceId=deviceId;
        value.deviceName=deviceName;
        value.code=String.format("%06d",random.nextInt(1_000_000));
        value.expiresAt=System.currentTimeMillis()+600_000L;
        challenges.put(value.id,value);
        return value;
    }

    public User verifyDeviceChallenge(String id,String code,String deviceId,String deviceName){
        var value=challenges.get(id);
        if(value==null||value.expiresAt<=System.currentTimeMillis()||!Objects.equals(value.code,code)||!Objects.equals(value.deviceId,deviceId)) return null;
        challenges.remove(id);
        trustedDevices.add(value.userId+":"+deviceId);
        return users.get(value.userId);
    }

    private SyncAcknowledgement applyOperation(String userId,boolean admin,SyncOperation operation){
        if(operation==null||operation.operationId==null||operation.operationId.isBlank()){
            return rejected(null,"Operação inválida");
        }
        SyncAcknowledgement processed=processedOperations.get(operation.operationId);
        if(processed!=null) return copy(processed);
        SyncAcknowledgement acknowledgement=switch(operation.type){
            case ACTIVITY_UPSERT -> applyActivityUpsert(admin,operation);
            case ACTIVITY_DELETE -> applyActivityDelete(admin,operation);
            case COMPLETION_CREATE -> applyCompletionCreate(userId,operation);
            default -> rejected(operation.operationId,"Tipo de operação não suportado");
        };
        processedOperations.put(operation.operationId,copy(acknowledgement));
        return acknowledgement;
    }

    private SyncAcknowledgement applyActivityUpsert(boolean admin,SyncOperation operation){
        if(!admin) return rejected(operation.operationId,"Somente administradores podem alterar atividades");
        String name=stringValue(operation.payload,"name");
        Area area=enumValue(Area.class,stringValue(operation.payload,"area"));
        Frequency frequency=enumValue(Frequency.class,stringValue(operation.payload,"frequency"));
        int effort=intValue(operation.payload,"effort",1);
        if(name==null||name.isBlank()||area==null||frequency==null){
            return rejected(operation.operationId,"Payload de atividade inválido");
        }
        Activity existing=activities.get(operation.entityId);
        long now=System.currentTimeMillis();
        if(existing!=null&&existing.serverRevision!=operation.baseRevision){
            return conflict(operation.operationId,existing,"Conflito de revisão");
        }
        Activity activity=existing==null?new Activity():existing;
        if(existing==null){
            activity.id=operation.entityId;
            activity.createdAt=now;
            activity.serverRevision=0L;
        }
        activity.name=name.trim();
        activity.area=area;
        activity.frequency=frequency;
        activity.effort=Math.max(1,Math.min(5,effort));
        activity.updatedAt=now;
        activity.deletedAt=0L;
        activity.syncStatus=SyncStatus.SYNCED;
        activity.serverRevision=activity.serverRevision+1L;
        tombstones.remove(activity.id);
        activities.put(activity.id,activity);
        recordChange(EntityType.ACTIVITY,activity.id);
        return applied(operation.operationId,activity.serverRevision);
    }

    private SyncAcknowledgement applyActivityDelete(boolean admin,SyncOperation operation){
        if(!admin) return rejected(operation.operationId,"Somente administradores podem remover atividades");
        Activity existing=activities.get(operation.entityId);
        if(existing==null){
            return alreadyApplied(operation.operationId,0L);
        }
        if(existing.serverRevision!=operation.baseRevision){
            return conflict(operation.operationId,existing,"Conflito de revisão");
        }
        long deletedAt=longValue(operation.payload,"deletedAt",System.currentTimeMillis());
        existing.updatedAt=deletedAt;
        existing.deletedAt=deletedAt;
        existing.serverRevision=existing.serverRevision+1L;
        existing.syncStatus=SyncStatus.DELETED;
        var tombstone=new Tombstone();
        tombstone.entityType=EntityType.ACTIVITY;
        tombstone.entityId=existing.id;
        tombstone.revision=existing.serverRevision;
        tombstone.deletedAt=deletedAt;
        tombstones.put(existing.id,tombstone);
        recordChange(EntityType.ACTIVITY,existing.id);
        return applied(operation.operationId,existing.serverRevision);
    }

    private SyncAcknowledgement applyCompletionCreate(String userId,SyncOperation operation){
        if(completions.containsKey(operation.entityId)){
            return alreadyApplied(operation.operationId,completions.get(operation.entityId).serverRevision);
        }
        String activityId=stringValue(operation.payload,"activitySyncId");
        Activity activity=activities.get(activityId);
        if(activity==null||activity.syncStatus==SyncStatus.DELETED){
            return rejected(operation.operationId,"Atividade não encontrada");
        }
        long completedAt=longValue(operation.payload,"completedAt",System.currentTimeMillis());
        long now=System.currentTimeMillis();
        var completion=new Completion();
        completion.id=operation.entityId;
        completion.activityId=activityId;
        completion.userId=userId;
        completion.completedAt=completedAt;
        completion.imagePath=stringValue(operation.payload,"imagePath");
        completion.isLate=booleanValue(operation.payload,"isLate");
        completion.createdAt=now;
        completion.updatedAt=now;
        completion.serverRevision=1L;
        completions.put(completion.id,completion);
        recordChange(EntityType.COMPLETION,completion.id);
        return applied(operation.operationId,completion.serverRevision);
    }

    private void recordChange(EntityType entityType,String entityId){
        changeLog.add(new ChangeRecord(++cursorSequence,entityType,entityId));
    }

    private static SyncAcknowledgement applied(String operationId,long revision){
        var ack=new SyncAcknowledgement();
        ack.operationId=operationId;
        ack.status=SyncAckStatus.APPLIED;
        ack.serverRevision=revision;
        return ack;
    }

    private static SyncAcknowledgement alreadyApplied(String operationId,long revision){
        var ack=new SyncAcknowledgement();
        ack.operationId=operationId;
        ack.status=SyncAckStatus.ALREADY_APPLIED;
        ack.serverRevision=revision;
        return ack;
    }

    private static SyncAcknowledgement rejected(String operationId,String message){
        var ack=new SyncAcknowledgement();
        ack.operationId=operationId;
        ack.status=SyncAckStatus.REJECTED;
        ack.message=message;
        return ack;
    }

    private static SyncAcknowledgement conflict(String operationId,Activity activity,String message){
        var ack=new SyncAcknowledgement();
        ack.operationId=operationId;
        ack.status=SyncAckStatus.CONFLICT;
        ack.serverRevision=activity.serverRevision;
        ack.conflict=copy(activity);
        ack.message=message;
        return ack;
    }

    private static FeaturePermissions nonNull(FeaturePermissions value){ return value==null?new FeaturePermissions():value; }
    private static <T> List<T> safe(List<T> values){ return values==null?List.of():values; }
    private static void require(boolean valid,String message){ if(!valid) throw new IllegalArgumentException(message); }

    private static String stringValue(Map<String,Object> payload,String key){
        if(payload==null) return null;
        Object value=payload.get(key);
        return value==null?null:value.toString();
    }

    private static long longValue(Map<String,Object> payload,String key,long fallback){
        if(payload==null) return fallback;
        Object value=payload.get(key);
        if(value instanceof Number number) return number.longValue();
        if(value instanceof String text&&!text.isBlank()) return Long.parseLong(text);
        return fallback;
    }

    private static int intValue(Map<String,Object> payload,String key,int fallback){
        if(payload==null) return fallback;
        Object value=payload.get(key);
        if(value instanceof Number number) return number.intValue();
        if(value instanceof String text&&!text.isBlank()) return Integer.parseInt(text);
        return fallback;
    }

    private static boolean booleanValue(Map<String,Object> payload,String key){
        if(payload==null) return false;
        Object value=payload.get(key);
        if(value instanceof Boolean bool) return bool;
        if(value instanceof String text) return Boolean.parseBoolean(text);
        return false;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type,String name){
        if(name==null||name.isBlank()) return null;
        return Enum.valueOf(type,name);
    }

    private static Activity copy(Activity source){
        var target=new Activity();
        target.id=source.id;
        target.name=source.name;
        target.area=source.area;
        target.frequency=source.frequency;
        target.effort=source.effort;
        target.createdAt=source.createdAt;
        target.updatedAt=source.updatedAt;
        target.serverRevision=source.serverRevision;
        target.deletedAt=source.deletedAt;
        target.syncStatus=source.syncStatus;
        return target;
    }

    private static Completion copy(Completion source){
        var target=new Completion();
        target.id=source.id;
        target.activityId=source.activityId;
        target.userId=source.userId;
        target.imagePath=source.imagePath;
        target.completedAt=source.completedAt;
        target.createdAt=source.createdAt;
        target.updatedAt=source.updatedAt;
        target.serverRevision=source.serverRevision;
        target.isLate=source.isLate;
        target.syncStatus=source.syncStatus;
        return target;
    }

    private static Tombstone copy(Tombstone source){
        var target=new Tombstone();
        target.entityType=source.entityType;
        target.entityId=source.entityId;
        target.revision=source.revision;
        target.deletedAt=source.deletedAt;
        return target;
    }

    private static SyncAcknowledgement copy(SyncAcknowledgement source){
        var target=new SyncAcknowledgement();
        target.operationId=source.operationId;
        target.status=source.status;
        target.serverRevision=source.serverRevision;
        target.message=source.message;
        target.conflict=source.conflict==null?null:copy(source.conflict);
        return target;
    }

    private record ChangeRecord(long cursor,EntityType entityType,String entityId){}
}
