package com.checklistboteco.backend.store;

import com.checklistboteco.backend.model.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import java.util.*;

/** Single-table DynamoDB adapter. The local profile deliberately uses LocalStore instead. */
@ApplicationScoped
@IfBuildProfile("prod")
public class DynamoDbStore extends LocalStore {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="checklist.dynamodb.table") String table;
    @ConfigProperty(name="checklist.aws.region") String region;
    private DynamoDbClient dynamo;
    private final Set<String> deletedUsers=new HashSet<>();

    @PostConstruct void connectAndLoad() {
        dynamo=DynamoDbClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create())
            .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        users.clear(); activities.clear(); completions.clear(); workClock.clear(); challenges.clear(); trustedDevices.clear(); deletedUsers.clear();
        dynamo.scan(ScanRequest.builder().tableName(table).build()).items().forEach(this::hydrate);
        deletedUsers.forEach(users::remove);
        seed();
    }
    @PreDestroy void close(){ if(dynamo!=null)dynamo.close(); }

    @Override public synchronized PublicUser createUser(CreateUserRequest request){ PublicUser result=super.createUser(request); put("USER",result.id,users.get(result.id)); return result; }
    @Override public synchronized PublicUser updateUser(String id,UpdateUserRequest request){ PublicUser result=super.updateUser(id,request); put("USER",id,users.get(id)); return result; }
    @Override public synchronized void deleteUser(String id){ super.deleteUser(id); putRaw("USER_DELETED",id,"deleted"); }
    @Override public synchronized PublicUser resetUserPassword(String id,String newPassword){ PublicUser result=super.resetUserPassword(id,newPassword); put("USER",id,users.get(id)); return result; }
    @Override public synchronized PublicUser updatePermissions(String id,FeaturePermissions permissions){ PublicUser result=super.updatePermissions(id,permissions); put("USER",id,users.get(id)); return result; }
    @Override public synchronized Activity createActivity(CreateActivityRequest request){ Activity result=super.createActivity(request); put("ACTIVITY",result.id,result); return result; }
    @Override public void upsertWorkClockEntries(List<WorkClockEntry> values){ super.upsertWorkClockEntries(values); safe(values).forEach(v->put("WORK_CLOCK",v.id,v)); }
    @Override public synchronized SyncPushResult pushSync(String userId,boolean admin,SyncPushRequest request){
        SyncPushResult result=super.pushSync(userId,admin,request);
        safe(request==null?null:request.operations).forEach(operation->{
            if(operation.entityId==null) return;
            Activity activity=activities.get(operation.entityId);
            if(activity!=null) put("ACTIVITY",activity.id,activity);
            Completion completion=completions.get(operation.entityId);
            if(completion!=null) put("COMPLETION",completion.id,completion);
            Tombstone tombstone=tombstones.get(operation.entityId);
            if(tombstone!=null) put("TOMBSTONE",tombstone.entityId,tombstone);
        });
        safe(request==null?null:request.workClockEntries).forEach(v->put("WORK_CLOCK",v.id,v));
        return result;
    }
    @Override public DeviceChallenge createDeviceChallenge(String userId,String deviceId,String deviceName){ DeviceChallenge result=super.createDeviceChallenge(userId,deviceId,deviceName); put("CHALLENGE",result.id,result); return result; }
    @Override public User verifyDeviceChallenge(String id,String code,String deviceId,String deviceName){
        User result=super.verifyDeviceChallenge(id,code,deviceId,deviceName);
        if(result!=null) putRaw("TRUSTED",result.id+":"+deviceId,result.id+":"+deviceId);
        return result;
    }
    private void put(String kind,String id,Object value){
        try { putRaw(kind,id,mapper.writeValueAsString(value)); } catch(Exception e){ throw new IllegalStateException("Falha ao gravar no DynamoDB",e); }
    }
    private void putRaw(String kind,String id,String payload){
        dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
            "pk",AttributeValue.fromS(kind+"#"+id),"kind",AttributeValue.fromS(kind),"payload",AttributeValue.fromS(payload)
        )).build());
    }
    private void hydrate(Map<String,AttributeValue> item){
        try {
            String kind=item.get("kind").s(), payload=item.get("payload").s();
            switch(kind){
                case "USER" -> { User value=mapper.readValue(payload,User.class); if(!deletedUsers.contains(value.id)) users.put(value.id,value); }
                case "USER_DELETED" -> { deletedUsers.add(item.get("pk").s().substring("USER_DELETED#".length())); }
                case "ACTIVITY" -> { Activity value=mapper.readValue(payload,Activity.class); activities.put(value.id,value); }
                case "COMPLETION" -> { Completion value=mapper.readValue(payload,Completion.class); completions.put(value.id,value); }
                case "WORK_CLOCK" -> { WorkClockEntry value=mapper.readValue(payload,WorkClockEntry.class); workClock.put(value.id,value); }
                case "TOMBSTONE" -> { Tombstone value=mapper.readValue(payload,Tombstone.class); tombstones.put(value.entityId,value); }
                case "CHALLENGE" -> { DeviceChallenge value=mapper.readValue(payload,DeviceChallenge.class); if(value.expiresAt>System.currentTimeMillis()) challenges.put(value.id,value); }
                case "TRUSTED" -> trustedDevices.add(payload);
                default -> { }
            }
        } catch(Exception e){ throw new IllegalStateException("Item inválido no DynamoDB",e); }
    }
    private static <T> List<T> safe(List<T> value){ return value==null?List.of():value; }
}
