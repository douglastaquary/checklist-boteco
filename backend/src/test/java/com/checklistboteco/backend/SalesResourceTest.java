package com.checklistboteco.backend;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SalesResourceTest {
    @Test void salesEndpointsRequireAdmin(){ given().when().get("/api/sales/schema").then().statusCode(401).body("message",is("Token inválido ou ausente")); }

    @Test void csvCanBePreviewedCommittedQueriedAndAggregated(){
        String token=adminToken(),dataset="sales-"+UUID.randomUUID();
        String csv="""
            Data;Produto;Categoria;Local;Quantidade;Valor;Canal
            10/06/2026;Cerveja Pilsen;Bebidas;Bar;12;180,00;Salão
            11/06/2026;Água com gás;Bebidas;Bar;8;48,00;Delivery
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200).body("totalRows",is(2)).body("headers",hasItem("Canal")).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        Map<String,String> mapping=new LinkedHashMap<>();
        mapping.put("saleDate","Data");
        mapping.put("description","Produto");
        mapping.put("category","Categoria");
        mapping.put("location","Local");
        mapping.put("quantity","Quantidade");
        mapping.put("totalInCents","Valor");
        given().auth().oauth2(token).contentType("application/json").body(Map.of("datasetId",dataset,"mapping",mapping,"preserveColumns",List.of("Canal")))
            .when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("status",is("COMMITTED")).body("importedRows",is(2)).body("rejectedRows",is(0));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-01","to","2026-06-30","text","delivery","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200).body("totalItems",is(1)).body("totalInCents",is(4800)).body("items[0].attributes.canal",is("Delivery"));
        given().auth().oauth2(token).when().get("/api/sales/schema?datasetId="+dataset).then().statusCode(200).body("saleCount",is(2)).body("fields.key",hasItem("canal"));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-01","to","2026-06-30","groupBy","description"))
            .when().post("/api/sales/aggregate?datasetId="+dataset).then().statusCode(200).body("totalItems",is(2)).body("groups.key",hasItems("Cerveja Pilsen","Água com gás"));
    }

    @Test void stockAuditAndMcpExposeReadOnlySalesTools(){
        String token=adminToken();
        String purchaseDataset="audit-purchases-"+UUID.randomUUID();
        String salesDataset="audit-sales-"+UUID.randomUUID();

        String purchasesCsv="""
            Data;Mercadoria;Categoria;Local;Quantidade;Valor
            10/06/2026;Cerveja Pilsen;Bebidas;Bar;10;100,00
            """;
        String purchasePreview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","compras.csv","csv",purchasesCsv))
            .when().post("/api/purchases/imports/preview").then().statusCode(200).extract().asString();
        String purchaseId=JsonPath.from(purchasePreview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",purchaseDataset,
            "mapping",Map.of("purchaseDate","Data","description","Mercadoria","category","Categoria","location","Local","quantity","Quantidade","totalInCents","Valor")
        )).when().post("/api/purchases/imports/"+purchaseId+"/commit").then().statusCode(200).body("importedRows",is(1));

        String salesCsv="""
            Data;Produto;Categoria;Local;Quantidade;Valor
            11/06/2026;Cerveja Pilsen;Bebidas;Bar;15;225,00
            """;
        String salesPreview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas.csv","csv",salesCsv))
            .when().post("/api/sales/imports/preview").then().statusCode(200).extract().asString();
        String salesId=JsonPath.from(salesPreview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",salesDataset,
            "mapping",Map.of("saleDate","Data","description","Produto","category","Categoria","location","Local","quantity","Quantidade","totalInCents","Valor")
        )).when().post("/api/sales/imports/"+salesId+"/commit").then().statusCode(200).body("importedRows",is(1));

        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "purchaseDatasetId",purchaseDataset,
            "salesDatasetId",salesDataset,
            "from","2026-06-01",
            "to","2026-06-30"
        )).when().post("/api/sales/audit/stock").then().statusCode(200)
            .body("totalItems",is(1))
            .body("items[0].status",is("ALERTA"))
            .body("items[0].differenceQuantity",anyOf(is(-5), is("-5")) );

        Map<String,Object> request=Map.of("jsonrpc","2.0","id",1,"method","tools/list","params",Map.of());
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(request).when().post("/mcp").then().statusCode(200)
            .body("result.tools.name",hasItems("sales_get_schema","sales_list","sales_aggregate","sales_by_product","sales_get_imports","sales_audit_stock"));
    }

    @Test void salesCsvWithoutDateAndLocationUsesDynamicMappingAndDefaults(){
        String token=adminToken(),dataset="sales-flex-"+UUID.randomUUID();
        String csv="""
            Cód Produto;Nome;Tipo Preço;Val. Unit;Qtde;Total Venda
            197;ADICIONAL DE BATATA;A Vista;6,9;3;20,7
            143;AGUA COM GAS;A Vista;6,989259;81;566,13
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","RelatorioVenda.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("suggestedMapping.description",is("Nome"))
            .body("suggestedMapping.quantity",is("Qtde"))
            .body("suggestedMapping.unitPriceInCents",is("Val. Unit"))
            .body("suggestedMapping.totalInCents",is("Total Venda"))
            .extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("description","Nome","quantity","Qtde","unitPriceInCents","Val. Unit","totalInCents","Total Venda","documentNumber","Cód Produto","unit","Tipo Preço"),
            "preserveColumns",List.of("Cód Produto","Nome","Tipo Preço","Val. Unit","Qtde","Total Venda")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(2)).body("rejectedRows",is(0));

        given().auth().oauth2(token).when().get("/api/sales/schema?datasetId="+dataset).then().statusCode(200).body("coverageFrom",notNullValue()).body("saleCount",is(2));
        given().auth().oauth2(token).contentType("application/json").body(Map.of("text","agua","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(1))
            .body("items[0].description",containsString("AGUA COM GAS"))
            .body("items[0].location",is("Não informado"))
            .body("items[0].documentNumber",is("143"))
            .body("items[0].unitPriceInCents",is(699))
            .body("items[0].totalInCents",is(56613));

        Map<String,Object> byProductRequest=Map.of(
            "jsonrpc","2.0",
            "id",2,
            "method","tools/call",
            "params",Map.of(
                "name","sales_by_product",
                "arguments",Map.of("datasetId",dataset,"product","agua")
            )
        );
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(byProductRequest).when().post("/mcp").then().statusCode(200)
            .body("result.structuredContent.totalItems",is(1))
            .body("result.structuredContent.totalQuantity",anyOf(is(81),is("81")))
            .body("result.structuredContent.items[0].description",containsString("AGUA COM GAS"));
    }

    private static String adminToken(){
        String device="sales-test-"+UUID.randomUUID();
        String first=given().contentType("application/json").body(Map.of("email","admin@checklistboteco.com","password","admin123","deviceId",device,"deviceName","JUnit sales"))
            .when().post("/api/auth/login").then().statusCode(200).extract().asString();
        JsonPath json=JsonPath.from(first);
        if(!json.getBoolean("requiresTwoFactor")) return json.getString("token");
        return given().contentType("application/json").body(Map.of("challengeId",json.getString("challengeId"),"code",json.getString("developmentCode"),"deviceId",device,"deviceName","JUnit sales"))
            .when().post("/api/auth/verify-device").then().statusCode(200).extract().path("token");
    }
}
