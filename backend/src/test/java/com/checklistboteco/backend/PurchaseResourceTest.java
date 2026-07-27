package com.checklistboteco.backend;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PurchaseResourceTest {
    @Test void purchaseEndpointsRequireAdmin(){ given().when().get("/api/purchases/schema").then().statusCode(401).body("message",is("Token inválido ou ausente")); }

    @Test void csvCanBePreviewedCommittedQueriedAndAggregated(){
        String token=adminToken(),dataset="test-"+UUID.randomUUID();
        String csv="""
            Data;Mercadoria;Categoria;Local;Fornecedor;Quantidade;Valor Total;Marca
            10/06/2026;Cerveja Pilsen;Bebidas;Bar;Distribuidora A;24;R$ 240,00;Marca X
            11/06/2026;Carne bovina;Alimentos;Cozinha;Frigorífico B;10;350,50;Marca Y
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","compras.csv","csv",csv))
            .when().post("/api/purchases/imports/preview").then().statusCode(200).body("totalRows",is(2)).body("headers",hasItem("Marca")).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        Map<String,String> mapping=new LinkedHashMap<>(); mapping.put("purchaseDate","Data"); mapping.put("description","Mercadoria"); mapping.put("category","Categoria"); mapping.put("location","Local"); mapping.put("supplier","Fornecedor"); mapping.put("quantity","Quantidade"); mapping.put("totalInCents","Valor Total");
        given().auth().oauth2(token).contentType("application/json").body(Map.of("datasetId",dataset,"mapping",mapping,"preserveColumns",List.of("Marca")))
            .when().post("/api/purchases/imports/"+id+"/commit").then().statusCode(200).body("status",is("COMMITTED")).body("importedRows",is(2)).body("rejectedRows",is(0));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-01","to","2026-06-30","categories",List.of("Bebidas"),"pageSize",50))
            .when().post("/api/purchases/query?datasetId="+dataset).then().statusCode(200).body("totalItems",is(1)).body("totalInCents",is(24000)).body("items[0].attributes.marca",is("Marca X"));
        given().auth().oauth2(token).when().get("/api/purchases/schema?datasetId="+dataset).then().statusCode(200).body("purchaseCount",is(2)).body("fields.key",hasItem("marca"));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-01","to","2026-06-30","groupBy","category"))
            .when().post("/api/purchases/aggregate?datasetId="+dataset).then().statusCode(200).body("totalItems",is(2)).body("groups.key",hasItems("Bebidas","Alimentos"));
    }

    @Test void mcpRequiresScopedTokenAndListsReadOnlyTools(){
        Map<String,Object> request=Map.of("jsonrpc","2.0","id",1,"method","tools/list","params",Map.of());
        given().contentType("application/json").body(request).when().post("/mcp").then().statusCode(401);
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(request).when().post("/mcp").then().statusCode(200)
            .body("result.tools.name",hasItems("purchases_get_schema","purchases_list","purchases_aggregate","purchases_get_imports"));
    }

    @Test void weeklyCmvReportFindsHeaderSkipsTotalsAndInfersYear(){
        String token=adminToken(),dataset="cmv-"+UUID.randomUUID();
        String csv="""
            ,CMV SEMANAL,,,jun. 2026
            ,1º SEMANA,,,
            ,LOCAL,CATEGORIA,DATA,VALOR
            ,Mercadão,Supermercado,01/06,"R$ 419,89"
            ,Ambev,Bebidas,02/06,"R$ 698,70"
            ,Total,,,"R$ 1.118,59"
            ,2º SEMANA,,,
            ,LOCAL,CATEGORIA,DATA,VALOR
            ,Tenda,Supermercado,09/06,"R$ 66,80"
            ,Gasto mensal,,,"R$ 1.185,39"
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","cmv.csv","csv",csv))
            .when().post("/api/purchases/imports/preview").then().statusCode(200).body("totalRows",is(3)).body("referenceYear",is(2026)).body("headers",hasItems("LOCAL","CATEGORIA","DATA","VALOR","Semana")).body("errors",empty()).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        Map<String,String> mapping=Map.of("purchaseDate","DATA","category","CATEGORIA","location","LOCAL","totalInCents","VALOR");
        given().auth().oauth2(token).contentType("application/json").body(Map.of("datasetId",dataset,"mapping",mapping,"preserveColumns",List.of("LOCAL","CATEGORIA","DATA","VALOR","Semana")))
            .when().post("/api/purchases/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(3)).body("rejectedRows",is(0));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-01","to","2026-06-30","locations",List.of("Ambev"),"minTotalInCents",60000,"pageSize",50))
            .when().post("/api/purchases/query?datasetId="+dataset).then().statusCode(200).body("totalItems",is(1)).body("items[0].purchaseDate",is("2026-06-02")).body("items[0].totalInCents",is(69870)).body("items[0].attributes.semana",is("1º SEMANA"));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2025-06-01","to","2026-06-30","text","supermercado","pageSize",50))
            .when().post("/api/purchases/query?datasetId="+dataset).then().statusCode(200).body("totalItems",is(2)).body("items.category",everyItem(is("Supermercado")));
    }

    @Test void csvKeepsDynamicColumnsButRequiresCoreMapping(){
        String token=adminToken(),dataset="dynamic-"+UUID.randomUUID();
        String csv="Quando;Grupo;Onde;Montante;Código;Cor\n10/06/2026;Estoque;Depósito;12,00;A-01;Azul\n11/06/2026;Estoque;Loja;8,00;A-02;Verde";
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","estoque.csv","csv",csv))
            .when().post("/api/purchases/imports/preview").then().statusCode(200).body("errors",hasSize(3)).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of("datasetId",dataset,"mapping",Map.of()))
            .when().post("/api/purchases/imports/"+id+"/commit").then().statusCode(400).body("message",containsString("Data, categoria, local e valor"));
        Map<String,String> mapping=Map.of("purchaseDate","Quando","category","Grupo","location","Onde","totalInCents","Montante");
        given().auth().oauth2(token).contentType("application/json").body(Map.of("datasetId",dataset,"mapping",mapping,"preserveColumns",List.of("Quando","Grupo","Onde","Montante","Código","Cor")))
            .when().post("/api/purchases/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(2)).body("rejectedRows",is(0));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-01","to","2026-06-30","pageSize",50))
            .when().post("/api/purchases/query?datasetId="+dataset).then().statusCode(200).body("totalItems",is(2)).body("items[0].location",notNullValue()).body("items[0].attributes",hasKey("codigo"));
        given().auth().oauth2(token).when().get("/api/purchases/schema?datasetId="+dataset).then().statusCode(200)
            .body("coverageFrom",notNullValue()).body("fields.key",hasItems("codigo","cor","quando","grupo","onde","montante"));
    }

    @Test void receiptSessionSubmitPersistsItems(){
        String token=adminToken(),dataset="receipt-"+UUID.randomUUID();
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("datasetId",dataset);
        payload.put("purchaseDate","2026-06-15");
        payload.put("location","Beco da Praia");
        payload.put("supplier","OMERC LTDA");
        payload.put("paymentMethod","Cartão Débito");
        payload.put("items",List.of(
            Map.of("description","CERVEJA HEINEKEN","category","Bebidas","quantity",24,"unitPriceInCents",450,"totalInCents",10800),
            Map.of("description","DETERGENTE NEUTRO","category","Limpeza","quantity",6,"unitPriceInCents",349,"totalInCents",2094)
        ));
        given().auth().oauth2(token).contentType("application/json").body(payload)
            .when().post("/api/purchases/receipt-sessions/submit")
            .then().statusCode(200).body("status",is("COMMITTED")).body("importedRows",is(2)).body("totalInCents",is(12894));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-01","to","2026-06-30","pageSize",50))
            .when().post("/api/purchases/query?datasetId="+dataset)
            .then().statusCode(200).body("totalItems",is(2)).body("items.category",hasItems("Bebidas","Limpeza"));
    }

    @Test void purchaseImportRequiresCanImportPurchasesPermission(){
        String device="purchases-user-"+UUID.randomUUID();
        String admin=adminToken();
        String create=given().auth().oauth2(admin).contentType("application/json").body(Map.of(
            "name","Compras User","email","compras-"+UUID.randomUUID()+"@test.com","password","Senha@123",
            "area","ATENDIMENTO","workSector","ATENDENTE","permissionLevel","USER",
            "permissions",Map.of("canImportPurchases",true)
        )).when().post("/api/users").then().statusCode(201).extract().asString();
        String email=JsonPath.from(create).getString("email");
        String first=given().contentType("application/json").body(Map.of("email",email,"password","Senha@123","deviceId",device,"deviceName","JUnit"))
            .when().post("/api/auth/login").then().statusCode(200).extract().asString();
        JsonPath login=JsonPath.from(first);
        String token=login.getBoolean("requiresTwoFactor")
            ? given().contentType("application/json").body(Map.of("challengeId",login.getString("challengeId"),"code",login.getString("developmentCode"),"deviceId",device,"deviceName","JUnit"))
                .when().post("/api/auth/verify-device").then().statusCode(200).extract().path("token")
            : login.getString("token");
        String csv="Data;Mercadoria;Categoria;Local;Valor Total\n15/06/2026;Item Teste;Bebidas;Beco da Praia;10,00\n";
        given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","ok.csv","csv",csv))
            .when().post("/api/purchases/imports/preview").then().statusCode(200).body("totalRows",is(1));
    }

    private static String adminToken(){
        String device="purchases-test-"+UUID.randomUUID();
        String first=given().contentType("application/json").body(Map.of("email","admin@checklistboteco.com","password","admin123","deviceId",device,"deviceName","JUnit purchases"))
            .when().post("/api/auth/login").then().statusCode(200).extract().asString();
        JsonPath json=JsonPath.from(first);
        if(!json.getBoolean("requiresTwoFactor")) return json.getString("token");
        return given().contentType("application/json").body(Map.of("challengeId",json.getString("challengeId"),"code",json.getString("developmentCode"),"deviceId",device,"deviceName","JUnit purchases"))
            .when().post("/api/auth/verify-device").then().statusCode(200).extract().path("token");
    }
}
