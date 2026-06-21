package com.checklistboteco.backend;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class ApiResourceTest {
    @Test void healthAndQuteAdminAreServed(){
        given().when().get("/api/health").then().statusCode(200).body("status",is("ok"));
        given().when().get("/").then().statusCode(200).contentType(containsString("text/html")).body(containsString("Equipe e permissões"));
    }

    @Test void loginRequiresDeviceConfirmationThenReturnsToken(){
        String first=given().contentType("application/json").body("""
            {"email":"admin@checklistboteco.com","password":"admin123","deviceId":"junit-device","deviceName":"JUnit"}
            """).when().post("/api/auth/login").then().statusCode(200).body("requiresTwoFactor",is(true)).extract().asString();
        var json=JsonPath.from(first);
        given().contentType("application/json").body(Map.of(
            "challengeId",json.getString("challengeId"),"code",json.getString("developmentCode"),"deviceId","junit-device","deviceName","JUnit"
        )).when().post("/api/auth/verify-device").then().statusCode(200).body("token",not(blankOrNullString())).body("user.permissionLevel",is("ADMIN"));
    }

    @Test void protectedEndpointRejectsAnonymousRequest(){
        given().when().get("/api/users").then().statusCode(401).body("message",is("Token inválido ou ausente"));
    }

    @Test void adminCanCreateEditResetAndDeleteUser(){
        String adminToken=login("admin@checklistboteco.com","admin123");
        String suffix=UUID.randomUUID().toString().substring(0,8);
        String email="colaborador+"+suffix+"@checklistboteco.com";

        String created=given()
            .header("Authorization","Bearer "+adminToken)
            .contentType("application/json")
            .body(Map.of(
                "name","Colaborador Teste",
                "email",email,
                "password","senha123",
                "workSector","GARCON",
                "permissionLevel","USER",
                "permissions",Map.of("canRegisterUsers",false,"canCreateActivities",false,"canEditUsers",false)
            ))
            .when().post("/api/users")
            .then().statusCode(201).body("email",is(email)).extract().asString();

        String userId=JsonPath.from(created).getString("id");

        given()
            .header("Authorization","Bearer "+adminToken)
            .contentType("application/json")
            .body(Map.of(
                "name","Colaborador Editado",
                "email","editado+"+suffix+"@checklistboteco.com",
                "workSector","COZINHA",
                "permissionLevel","USER"
            ))
            .when().put("/api/users/"+userId)
            .then().statusCode(200)
            .body("name",is("Colaborador Editado"))
            .body("workSector",is("COZINHA"));

        given()
            .header("Authorization","Bearer "+adminToken)
            .contentType("application/json")
            .body(Map.of("newPassword","novaSenha123"))
            .when().post("/api/users/"+userId+"/reset-password")
            .then().statusCode(200)
            .body("id",is(userId));

        String userToken=login("editado+"+suffix+"@checklistboteco.com","novaSenha123");
        given().header("Authorization","Bearer "+userToken).when().get("/api/me").then().statusCode(200).body("name",is("Colaborador Editado"));

        given()
            .header("Authorization","Bearer "+adminToken)
            .contentType("application/json")
            .body(Map.of("permissions",Map.of(
                "canRegisterUsers",false,
                "canCreateActivities",false,
                "canEditUsers",false,
                "canCreateInventoryCounts",true,
                "canViewInventoryInsights",false
            )))
            .when().patch("/api/users/"+userId+"/permissions")
            .then().statusCode(200)
            .body("permissions.canCreateInventoryCounts",is(true));

        given()
            .header("Authorization","Bearer "+adminToken)
            .when().delete("/api/users/"+userId)
            .then().statusCode(204);

        given()
            .header("Authorization","Bearer "+adminToken)
            .when().get("/api/users")
            .then().statusCode(200)
            .body("id",org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(userId)));
    }

    @Test void delegatedPermissionsRespectCreateEditBoundaries(){
        String adminToken=login("admin@checklistboteco.com","admin123");
        String suffix=UUID.randomUUID().toString().substring(0,8);

        String managerBody=given()
            .header("Authorization","Bearer "+adminToken)
            .contentType("application/json")
            .body(Map.of(
                "name","Gestor Equipe "+suffix,
                "email","gestor+"+suffix+"@checklistboteco.com",
                "password","senha123",
                "workSector","GERENTE",
                "permissionLevel","USER",
                "permissions",Map.of("canRegisterUsers",true,"canCreateActivities",false,"canEditUsers",false)
            ))
            .when().post("/api/users")
            .then().statusCode(201).extract().asString();
        String managerId=JsonPath.from(managerBody).getString("id");

        String managerToken=login("gestor+"+suffix+"@checklistboteco.com","senha123");

        given()
            .header("Authorization","Bearer "+managerToken)
            .when().get("/api/users")
            .then().statusCode(200);

        String createdByDelegate=given()
            .header("Authorization","Bearer "+managerToken)
            .contentType("application/json")
            .body(Map.of(
                "name","Novo Usuario "+suffix,
                "email","novo+"+suffix+"@checklistboteco.com",
                "password","senha123",
                "workSector","CUMIM",
                "permissionLevel","USER",
                "permissions",Map.of("canRegisterUsers",false,"canCreateActivities",false,"canEditUsers",false)
            ))
            .when().post("/api/users")
            .then().statusCode(201).extract().asString();
        String createdUserId=JsonPath.from(createdByDelegate).getString("id");

        given()
            .header("Authorization","Bearer "+managerToken)
            .contentType("application/json")
            .body(Map.of(
                "name","Sem Permissão",
                "email","novo+"+suffix+"@checklistboteco.com",
                "workSector","GARCON",
                "permissionLevel","USER"
            ))
            .when().put("/api/users/"+createdUserId)
            .then().statusCode(403)
            .body("message",is("Permissão para editar usuários necessária"));

        given()
            .header("Authorization","Bearer "+managerToken)
            .contentType("application/json")
            .body(Map.of("permissions",Map.of("canRegisterUsers",false,"canCreateActivities",true,"canEditUsers",true)))
            .when().patch("/api/users/"+createdUserId+"/permissions")
            .then().statusCode(403)
            .body("message",is("Permissão administrativa necessária"));

        given().header("Authorization","Bearer "+adminToken).when().delete("/api/users/"+createdUserId).then().statusCode(204);
        given().header("Authorization","Bearer "+adminToken).when().delete("/api/users/"+managerId).then().statusCode(204);
    }

    private String login(String email,String password){
        String deviceId="device-"+UUID.randomUUID();
        String first=given()
            .contentType("application/json")
            .body(Map.of("email",email,"password",password,"deviceId",deviceId,"deviceName","JUnit"))
            .when().post("/api/auth/login")
            .then().statusCode(200)
            .extract().asString();
        JsonPath json=JsonPath.from(first);
        if(Boolean.TRUE.equals(json.getBoolean("requiresTwoFactor"))){
            return given()
                .contentType("application/json")
                .body(Map.of(
                    "challengeId",json.getString("challengeId"),
                    "code",json.getString("developmentCode"),
                    "deviceId",deviceId,
                    "deviceName","JUnit"
                ))
                .when().post("/api/auth/verify-device")
                .then().statusCode(200)
                .extract().path("token");
        }
        return json.getString("token");
    }
}
