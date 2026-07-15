package com.checklistboteco.backend.web;

import com.checklistboteco.backend.model.Models.*;
import com.checklistboteco.backend.security.TokenService;
import com.checklistboteco.backend.security.AdminGuard;
import com.checklistboteco.backend.store.AppStore;
import com.checklistboteco.backend.checklist.ChecklistTimingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.List;
import java.time.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ApiResource {
    @Inject AppStore store;
    @Inject TokenService tokens;
    @Inject AdminGuard guard;
    @Inject ChecklistTimingService checklistTiming;
    @ConfigProperty(name="checklist.auth.expose-device-code") boolean exposeDeviceCode;
    @ConfigProperty(name="worksite.radiusMeters", defaultValue="5") double worksiteRadiusMeters;

    @GET @Path("/health") public Object health(){ return java.util.Map.of("status","ok"); }

    @POST @Path("/auth/login") public LoginResponse login(LoginRequest request){
        User user=store.authenticate(request==null?null:request.email,request==null?null:request.password);
        if(user==null) fail(Response.Status.UNAUTHORIZED,"Email ou senha inválidos");
        String device=request.deviceId==null||request.deviceId.isBlank()?"unknown-device":request.deviceId.trim();
        if(!store.isTrustedDevice(user.id,device)){
            DeviceChallenge challenge=store.createDeviceChallenge(user.id,device,request.deviceName); var result=new LoginResponse();
            result.requiresTwoFactor=true; result.challengeId=challenge.id; result.deliveryHint="Código de verificação gerado para confirmação do dispositivo";
            if(exposeDeviceCode) result.developmentCode=challenge.code;
            return result;
        }
        return authenticated(user);
    }
    @POST @Path("/auth/verify-device") public LoginResponse verifyDevice(VerifyDeviceRequest request){
        if(request==null) fail(Response.Status.BAD_REQUEST,"Requisição inválida");
        String challengeId=request.challengeId==null?null:request.challengeId.trim();
        String code=request.code==null?null:request.code.trim();
        String device=request.deviceId==null||request.deviceId.isBlank()?"unknown-device":request.deviceId.trim();
        User user=store.verifyDeviceChallenge(challengeId,code,device,request.deviceName);
        if(user==null) fail(Response.Status.UNAUTHORIZED,"Código de verificação inválido ou expirado");
        return authenticated(user);
    }
    @GET @Path("/me") public PublicUser me(@HeaderParam("Authorization") String auth){
        TokenService.Payload payload=requireToken(auth); User user=store.getUser(payload.userId); if(user==null) fail(Response.Status.UNAUTHORIZED,"Usuário não encontrado"); return PublicUser.from(user);
    }
    @POST @Path("/me/change-password") public PublicUser changeOwnPassword(@HeaderParam("Authorization") String auth,ChangePasswordRequest request){
        TokenService.Payload payload=requireToken(auth); if(request==null) fail(Response.Status.BAD_REQUEST,"Dados de senha obrigatórios");
        return store.changeOwnPassword(payload.userId,request.currentPassword,request.newPassword);
    }
    @GET @Path("/users") public List<PublicUser> users(@HeaderParam("Authorization") String auth){ guard.requireUserManagementReadAccess(auth); return store.users(); }
    @POST @Path("/users") public Response createUser(@HeaderParam("Authorization") String auth,CreateUserRequest request){
        guard.requireUserManagementAccess(auth,true); return Response.status(Response.Status.CREATED).entity(store.createUser(request)).build();
    }
    @PUT @Path("/users/{id}") public PublicUser updateUser(@HeaderParam("Authorization") String auth,@PathParam("id") String id,UpdateUserRequest request){
        guard.requireUserManagementAccess(auth,false); return store.updateUser(id,request);
    }
    @DELETE @Path("/users/{id}") public Response deleteUser(@HeaderParam("Authorization") String auth,@PathParam("id") String id){
        guard.requireUserManagementAccess(auth,false); store.deleteUser(id); return Response.noContent().build();
    }
    @POST @Path("/users/{id}/reset-password") public PublicUser resetPassword(@HeaderParam("Authorization") String auth,@PathParam("id") String id,ResetPasswordRequest request){
        guard.requireUserManagementAccess(auth,false); if(request==null) fail(Response.Status.BAD_REQUEST,"Nova senha obrigatória"); return store.resetUserPassword(id,request.newPassword);
    }
    @PATCH @Path("/users/{id}/permissions") public PublicUser permissions(@HeaderParam("Authorization") String auth,@PathParam("id") String id,PermissionUpdateRequest request){
        requireAdmin(auth); if(request==null) fail(Response.Status.BAD_REQUEST,"Permissões obrigatórias"); return store.updatePermissions(id,request.permissions);
    }
    @GET @Path("/activities") public List<Activity> activities(@HeaderParam("Authorization") String auth){ requireToken(auth); return store.activities(); }
    @POST @Path("/activities") public Response createActivity(@HeaderParam("Authorization") String auth,CreateActivityRequest request){
        guard.requireActivityManagementAccess(auth); return Response.status(Response.Status.CREATED).entity(store.createActivity(request)).build();
    }
    @PUT @Path("/activities/{id}") public Activity updateActivity(@HeaderParam("Authorization") String auth,@PathParam("id") String id,CreateActivityRequest request){
        guard.requireActivityManagementAccess(auth); return store.updateActivity(id,request);
    }
    @GET @Path("/completions") public List<Completion> completions(@HeaderParam("Authorization") String auth){ requireToken(auth); return store.completions(); }
    @GET @Path("/checklist/schedule") public ChecklistSchedule checklistSchedule(@HeaderParam("Authorization") String auth){ requireToken(auth); return store.checklistSchedule(); }
    @PUT @Path("/checklist/schedule") public ChecklistSchedule updateChecklistSchedule(@HeaderParam("Authorization") String auth,ChecklistSchedule schedule){ requireAdmin(auth); return store.saveChecklistSchedule(schedule); }
    @GET @Path("/checklist/today") public ChecklistOverview checklistToday(@HeaderParam("Authorization") String auth,@QueryParam("date") String rawDate){
        TokenService.Payload payload=requireToken(auth); User user=store.getUser(payload.userId); if(user==null) fail(Response.Status.UNAUTHORIZED,"Usuário não encontrado");
        ChecklistOverview result=checklistOverview(rawDate); if(payload.isAdmin) return result;
        result.occurrences=result.occurrences.stream().filter(value->value.assigneeIds.isEmpty()?user.allowedAreas.contains(value.area):value.assigneeIds.contains(user.id)).toList();
        recount(result); return result;
    }
    @GET @Path("/admin/checklist/overview") public ChecklistOverview adminChecklistOverview(@HeaderParam("Authorization") String auth,@QueryParam("date") String rawDate){ requireAdmin(auth); return checklistOverview(rawDate); }
    @GET @Path("/admin/dashboard") public DashboardStats dashboard(@HeaderParam("Authorization") String auth){ requireAdmin(auth); return store.dashboard(); }
    @GET @Path("/sync/pull") public SyncPullResponse pull(@HeaderParam("Authorization") String auth,@QueryParam("cursor") @DefaultValue("0") String cursor,@QueryParam("limit") @DefaultValue("500") int limit){
        TokenService.Payload payload=requireToken(auth); return response(store.pullChanges(payload.userId,parseCursor(cursor),Math.max(1,Math.min(500,limit))));
    }
    @POST @Path("/sync/push") public SyncPushResult push(@HeaderParam("Authorization") String auth,SyncPushRequest request){
        TokenService.Payload payload=requireToken(auth); if(request==null) request=new SyncPushRequest();
        validateWorkClockEntries(payload, request.workClockEntries);
        return store.pushSync(payload.userId,payload.isAdmin,request);
    }
    private LoginResponse authenticated(User user){ var result=new LoginResponse(); result.token=tokens.issue(user.id,user.permissionLevel==PermissionLevel.ADMIN); result.user=PublicUser.from(user); return result; }
    private SyncPullResponse response(PullData data){ var result=new SyncPullResponse(); result.serverTime=System.currentTimeMillis(); result.nextCursor=data.nextCursor; result.hasMore=data.hasMore; result.activities=data.activities; result.completions=data.completions; result.tombstones=data.tombstones; result.checklistSchedule=store.checklistSchedule(); return result; }
    private TokenService.Payload requireToken(String authorization){ return guard.requireToken(authorization); }
    private void requireAdmin(String auth){ guard.requireAdmin(auth); }
    private void validateWorkClockEntries(TokenService.Payload payload,List<WorkClockEntry> entries){
        for(WorkClockEntry entry: entries==null?List.<WorkClockEntry>of():entries){
            if(entry==null) fail(Response.Status.BAD_REQUEST,"Marcação de ponto inválida");
            if(!payload.userId.equals(entry.userId)) fail(Response.Status.FORBIDDEN,"Não é permitido enviar ponto de outro usuário");
            if(entry.distanceFromWorkMeters>worksiteRadiusMeters) fail(Response.Status.BAD_REQUEST,"Marcação de ponto fora do raio permitido");
        }
    }
    private ChecklistOverview checklistOverview(String rawDate){
        ChecklistSchedule schedule=store.checklistSchedule(); ZoneId zone=ZoneId.of(schedule.timezone);
        LocalDate date=rawDate==null||rawDate.isBlank()?LocalDate.now(zone):LocalDate.parse(rawDate);
        return checklistTiming.overview(date,System.currentTimeMillis(),store.activities(),store.completions(),store.users(),schedule);
    }
    private static void recount(ChecklistOverview value){
        value.green=value.yellow=value.red=value.completed=value.totalRemainingMinutes=0;
        for(ChecklistOccurrence item:value.occurrences) switch(item.status){
            case GREEN->{value.green++;value.totalRemainingMinutes+=item.estimatedDurationMinutes;}
            case YELLOW->{value.yellow++;value.totalRemainingMinutes+=item.estimatedDurationMinutes;}
            case RED->{value.red++;value.totalRemainingMinutes+=item.estimatedDurationMinutes;}
            case COMPLETED->value.completed++;
        }
    }
    private static long parseCursor(String cursor){ try{return cursor==null||cursor.isBlank()?0L:Long.parseLong(cursor);}catch(NumberFormatException error){ return 0L; } }
    private static void fail(Response.Status status,String message){ throw new WebApplicationException(Response.status(status).entity(new ApiError(message)).type(MediaType.APPLICATION_JSON).build()); }
}
