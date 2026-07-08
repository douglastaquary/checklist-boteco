package com.checklistboteco.backend;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class InventoryResourceTest {
    @Test void mcpExposesReadOnlyInventoryTools(){
        given().header("Authorization","Bearer local-purchases-token").contentType("application/json")
            .body(Map.of("jsonrpc","2.0","id",1,"method","tools/list","params",Map.of()))
            .post("/mcp").then().statusCode(200)
            .body("result.tools.name",hasItems("inventory_count_sessions","inventory_daily_audit"));
    }

    @Test void delegatedUserSubmitsImmutableCountWithServerOwnedAuditFields(){
        String admin=login("admin@checklistboteco.com","admin123"); String suffix=UUID.randomUUID().toString().substring(0,8); String email="contador+"+suffix+"@teste.com";
        String created=given().header("Authorization","Bearer "+admin).contentType("application/json").body(Map.of(
            "name","Contador "+suffix,"email",email,"password","senha123","workSector","BARMAN","permissionLevel","USER",
            "permissions",Map.of("canCreateInventoryCounts",true,"canViewInventoryInsights",true)
        )).post("/api/users").then().statusCode(201).extract().asString();
        String userId=JsonPath.from(created).getString("id"); String token=login(email,"senha123");
        String product="Heineken Teste "+suffix;
        String session=given().header("Authorization","Bearer "+token).contentType("application/json").body(("""
            {"countDate":"2026-06-20","countedAt":"2026-06-20T20:15:00Z","location":"beco","items":[
              {"name":"%s","quantity":24,"category":"ALCOOLICO","volume":600,"volumeUnit":"ML","salePriceInCents":1800,"costPriceInCents":900,"condition":"GELADO"}
            ]}
            """).formatted(product)).post("/api/inventory/counts").then().statusCode(201)
            .body("createdBy",is(userId)).body("createdByName",startsWith("Contador ")).body("countedAt",startsWith("2026-06-20T20:15:00")).body("submittedAt",not(blankOrNullString())).body("status",is("SUBMITTED")).extract().asString();
        String id=JsonPath.from(session).getString("id");

        given().header("Authorization","Bearer "+token).contentType("application/json").body("{}").put("/api/inventory/counts/"+id).then().statusCode(anyOf(is(404),is(405)));
        given().header("Authorization","Bearer "+token).contentType("application/json").body(Map.of("date","2026-06-20","text",suffix)).post("/api/inventory/audit/daily").then().statusCode(200).body("items[0].product",is(product)).body("items[0].theoreticalRemaining",is(24));
        given().header("Authorization","Bearer "+token).delete("/api/inventory/counts/"+id).then().statusCode(403);
        given().header("Authorization","Bearer "+admin).delete("/api/inventory/counts/"+id).then().statusCode(204);
        given().header("Authorization","Bearer "+admin).delete("/api/users/"+userId).then().statusCode(204);
    }

    @Test void administrativeStockAddsBalanceAndApplyAuditSubtractsSoldQuantity(){
        String admin=login("admin@checklistboteco.com","admin123");
        String suffix=UUID.randomUUID().toString().substring(0,8);
        String email="estoque+"+suffix+"@teste.com";
        String created=given().header("Authorization","Bearer "+admin).contentType("application/json").body(Map.of(
            "name","Estoque "+suffix,"email",email,"password","senha123","workSector","GERENTE","permissionLevel","USER",
            "permissions",Map.of("canManageAdministrativeStock",true,"canViewInventoryInsights",true)
        )).post("/api/users").then().statusCode(201).extract().asString();
        String userId=JsonPath.from(created).getString("id");
        String token=login(email,"senha123");
        String product="Produto Admin "+suffix;
        long dateOffset=Long.parseUnsignedLong(suffix,16)%1_000_000L;
        String auditDate=LocalDate.of(2100,1,1).plusDays(dateOffset).toString();

        given().header("Authorization","Bearer "+token).contentType("application/json").body(("""
            {"countDate":"%s","countedAt":"%sT10:00:00Z","location":"Beco da Praia","items":[
              {"name":"%s","quantity":100,"category":"ALCOOLICO","volume":600,"volumeUnit":"ML","salePriceInCents":1800,"condition":"GELADO"}
            ]}
            """).formatted(auditDate,auditDate,product)).post("/api/inventory/admin-stock/counts").then().statusCode(201);

        given().header("Authorization","Bearer "+token).get("/api/inventory/admin-stock/balances").then().statusCode(200)
            .body("find { it.productName == '"+product+"' }.quantity",is(100));

        given().header("Authorization","Bearer "+admin).contentType("application/json").body(("""
            {"countDate":"%s","countedAt":"%sT08:00:00Z","location":"Beco da Praia","items":[
              {"name":"%s","quantity":100,"category":"ALCOOLICO","volume":600,"volumeUnit":"ML","salePriceInCents":1800,"condition":"GELADO"}
            ]}
            """).formatted(auditDate,auditDate,product)).post("/api/inventory/counts").then().statusCode(201);

        String csv="Data;Produto;Categoria;Local;Quantidade;Valor\n"+auditDate+";"+product+";Bebidas;Beco da Praia;15;270,00\n";
        String preview=given().header("Authorization","Bearer "+token).contentType("application/json").body(Map.of("fileName","vendas.csv","csv",csv))
            .post("/api/sales/imports/preview").then().statusCode(200).extract().asString();
        String importId=JsonPath.from(preview).getString("id");
        Map<String,String> mapping=new LinkedHashMap<>();
        mapping.put("saleDate","Data");
        mapping.put("description","Produto");
        mapping.put("category","Categoria");
        mapping.put("location","Local");
        mapping.put("quantity","Quantidade");
        mapping.put("totalInCents","Valor");
        given().header("Authorization","Bearer "+token).contentType("application/json").body(Map.of("datasetId","sales","mapping",mapping))
            .post("/api/sales/imports/"+importId+"/commit").then().statusCode(200).body("importedRows",is(1));

        given().header("Authorization","Bearer "+token).contentType("application/json").body(Map.of("date",auditDate,"location","Beco da Praia","text",suffix))
            .post("/api/inventory/audit/daily/apply").then().statusCode(200).body("alreadyApplied",is(false));

        given().header("Authorization","Bearer "+token).get("/api/inventory/admin-stock/balances").then().statusCode(200)
            .body("find { it.productName == '"+product+"' }.quantity",is(85));

        given().header("Authorization","Bearer "+token).contentType("application/json").body(Map.of("date",auditDate,"location","Beco da Praia","text",suffix))
            .post("/api/inventory/audit/daily/apply").then().statusCode(200).body("alreadyApplied",is(true));

        given().header("Authorization","Bearer "+admin).delete("/api/users/"+userId).then().statusCode(204);
    }

    private String login(String email,String password){ String device="inventory-"+UUID.randomUUID(); String first=given().contentType("application/json").body(Map.of("email",email,"password",password,"deviceId",device,"deviceName","JUnit")).post("/api/auth/login").then().statusCode(200).extract().asString(); JsonPath json=JsonPath.from(first); return given().contentType("application/json").body(Map.of("challengeId",json.getString("challengeId"),"code",json.getString("developmentCode"),"deviceId",device,"deviceName","JUnit")).post("/api/auth/verify-device").then().statusCode(200).extract().path("token"); }
}
