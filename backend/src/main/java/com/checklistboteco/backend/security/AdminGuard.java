package com.checklistboteco.backend.security;

import com.checklistboteco.backend.model.Models.ApiError;
import com.checklistboteco.backend.model.Models.FeaturePermissions;
import com.checklistboteco.backend.model.Models.PermissionLevel;
import com.checklistboteco.backend.model.Models.User;
import com.checklistboteco.backend.store.AppStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class AdminGuard {
    @Inject TokenService tokens;
    @Inject AppStore store;

    public TokenService.Payload requireToken(String authorization) {
        String raw=authorization!=null&&authorization.startsWith("Bearer ")?authorization.substring(7).trim():"";
        TokenService.Payload payload=tokens.verify(raw);
        if(payload==null) fail(Response.Status.UNAUTHORIZED,"Token inválido ou ausente");
        return payload;
    }

    public User requireAdmin(String authorization) {
        TokenService.Payload payload=requireToken(authorization);
        User user=store.getUser(payload.userId);
        if(user==null||user.permissionLevel!=PermissionLevel.ADMIN) fail(Response.Status.FORBIDDEN,"Permissão administrativa necessária");
        return user;
    }

    public User requireUserManagementAccess(String authorization, boolean forCreate) {
        TokenService.Payload payload=requireToken(authorization);
        User user=store.getUser(payload.userId);
        if(user==null) fail(Response.Status.UNAUTHORIZED,"Usuário não encontrado");
        if(user.permissionLevel==PermissionLevel.ADMIN) return user;
        FeaturePermissions permissions=user.permissions==null?new FeaturePermissions():user.permissions;
        boolean allowed=forCreate?permissions.canRegisterUsers:permissions.canEditUsers;
        if(!allowed) fail(Response.Status.FORBIDDEN,forCreate?"Permissão para cadastrar usuários necessária":"Permissão para editar usuários necessária");
        return user;
    }

    public User requireUserManagementReadAccess(String authorization) {
        TokenService.Payload payload=requireToken(authorization);
        User user=store.getUser(payload.userId);
        if(user==null) fail(Response.Status.UNAUTHORIZED,"Usuário não encontrado");
        if(user.permissionLevel==PermissionLevel.ADMIN) return user;
        FeaturePermissions permissions=user.permissions==null?new FeaturePermissions():user.permissions;
        if(permissions.canRegisterUsers || permissions.canEditUsers) return user;
        fail(Response.Status.FORBIDDEN,"Permissão para acessar a equipe necessária");
        return user;
    }

    public User requireActivityManagementAccess(String authorization) {
        TokenService.Payload payload=requireToken(authorization);
        User user=store.getUser(payload.userId);
        if(user==null) fail(Response.Status.UNAUTHORIZED,"Usuário não encontrado");
        if(user.permissionLevel==PermissionLevel.ADMIN || (user.permissions!=null && user.permissions.canCreateActivities)) return user;
        fail(Response.Status.FORBIDDEN,"Permissão para gerenciar atividades necessária");
        return user;
    }

    public static void fail(Response.Status status,String message) {
        throw new WebApplicationException(Response.status(status).entity(new ApiError(message)).type(MediaType.APPLICATION_JSON).build());
    }
}
