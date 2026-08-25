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

    @Test void salesCanBeAggregatedByMonthThroughMcp(){
        String token=adminToken(),dataset="sales-month-"+UUID.randomUUID();
        String csv="""
            Data;Produto;Quantidade;Valor
            10/05/2026;Cerveja Pilsen;10;200,00
            20/05/2026;Água com gás;20;100,00
            05/06/2026;Cerveja Pilsen;6;120,00
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-mensais.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","quantity","Quantidade","totalInCents","Valor")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(3));

        Map<String,Object> request=Map.of(
            "jsonrpc","2.0",
            "id",2,
            "method","tools/call",
            "params",Map.of(
                "name","sales_aggregate",
                "arguments",Map.of("datasetId",dataset,"from","2026-05-01","to","2026-06-30","groupBy","month")
            )
        );
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(request)
            .when().post("/mcp").then().statusCode(200)
            .body("result.structuredContent.groupBy",is("month"))
            .body("result.structuredContent.groups.find { it.key == '2026-05' }.totalInCents",is(30000))
            .body("result.structuredContent.groups.find { it.key == '2026-06' }.totalInCents",is(12000));
    }

    @Test void salesMonthCompareExplainsRevenueDriversThroughMcp(){
        String token=adminToken(),dataset="sales-month-compare-"+UUID.randomUUID();
        String csv="""
            Data;Produto;Quantidade;Valor
            01/04/2026;ÁGUA COM GÁS;5;100,00
            30/04/2026;HEINEKEN 600ML;5;100,00
            02/05/2026;HEINEKEN 600ML;10;500,00
            17/05/2026;HEINEKEN 600ML;10;500,00
            30/05/2026;HEINEKEN 600ML;10;500,00
            01/06/2026;ÁGUA COM GÁS;5;100,00
            30/06/2026;HEINEKEN 600ML;5;100,00
            01/07/2026;ÁGUA COM GÁS;5;100,00
            31/07/2026;HEINEKEN 600ML;5;100,00
            15/08/2026;HEINEKEN 600ML;50;2.000,00
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","comparacao-mensal.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","quantity","Quantidade","totalInCents","Valor")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(10));

        Map<String,Object> request=Map.of(
            "jsonrpc","2.0",
            "id",3,
            "method","tools/call",
            "params",Map.of(
                "name","sales_month_compare",
                "arguments",Map.of("datasetId",dataset,"focusMonth","2026-05","from","2026-04-01","to","2026-08-31","topProducts",5)
            )
        );
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(request)
            .when().post("/mcp").then().statusCode(200)
            .body("result.structuredContent.focusMonth",is("2026-05"))
            .body("result.structuredContent.baselineMonths",containsInAnyOrder("2026-04","2026-06","2026-07"))
            .body("result.structuredContent.focus.totalInCents",is(150000))
            .body("result.structuredContent.baselineAverage.totalInCents",is(20000))
            .body("result.structuredContent.delta.revenueInCents",is(130000))
            .body("result.structuredContent.topProductDrivers[0].product",is("HEINEKEN 600ML"))
            .body("result.structuredContent.topDays[0].dayOfWeek",anyOf(is("SATURDAY"),is("SUNDAY")))
            .body("result.structuredContent.findings",not(empty()));
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
            .body("result.tools.name",hasItems("sales_get_schema","sales_list","sales_aggregate","sales_month_compare","sales_by_product","sales_quantity_by_product_in_period","sales_by_seller","sales_get_imports","sales_audit_stock"));
    }

    @Test void salesImportAggregatesSellerAndServiceChargeForMcpAndAdminQueries(){
        String token=adminToken(),dataset="sales-seller-"+UUID.randomUUID();
        String csv="""
            Data;Produto;Local;Garçom;Quantidade;Valor
            14/07/2026;HEINEKEN 600ML;Beco da Praia;João Rodrigues;2;40,00
            14/07/2026;CAIPIRINHA;Beco da Praia;João Rodrigues;1;30,00
            14/07/2026;AGUA;Beco da Praia;Maria Souza;3;24,00
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-garcom.csv","csv",csv,"datasetId",dataset))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("suggestedMapping.seller",is("Garçom"))
            .extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","location","Local","seller","Garçom","quantity","Quantidade","totalInCents","Valor")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200)
            .body("importedRows",is(3));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-07-14","to","2026-07-14","sellers",List.of("João Rodrigues"),"pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(2))
            .body("totalInCents",is(7000))
            .body("serviceChargeInCents",is(700))
            .body("items.seller",everyItem(is("João Rodrigues")));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-07-14","to","2026-07-14","groupBy","seller"))
            .when().post("/api/sales/aggregate?datasetId="+dataset).then().statusCode(200)
            .body("totalInCents",is(9400))
            .body("serviceChargeInCents",is(940))
            .body("groups.find { it.key == 'João Rodrigues' }.totalInCents",is(7000))
            .body("groups.find { it.key == 'João Rodrigues' }.serviceChargeInCents",is(700));

        Map<String,Object> bySellerRequest=Map.of(
            "jsonrpc","2.0",
            "id",5,
            "method","tools/call",
            "params",Map.of(
                "name","sales_by_seller",
                "arguments",Map.of("datasetId",dataset,"seller","João Rodrigues","from","2026-07-14","to","2026-07-14")
            )
        );
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(bySellerRequest).when().post("/mcp").then().statusCode(200)
            .body("result.structuredContent.totalItems",is(2))
            .body("result.structuredContent.totalInCents",is(7000))
            .body("result.structuredContent.serviceChargeInCents",is(700))
            .body("result.structuredContent.items[0].seller",is("João Rodrigues"));
    }

    @Test void salesCsvWithoutDateAndLocationUsesDynamicMappingAndDefaults(){
        String token=adminToken(),dataset="sales-flex-"+UUID.randomUUID();
        String csv="""
            Cód Produto;Nome;Tipo Preço;Val. Unit;Qtde;Total Venda
            10/06/2026;;;;;
            197;ADICIONAL DE BATATA;A Vista;6,9;3;20,7
            143;AGUA COM GAS;A Vista;6,989259;81;566,13
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","RelatorioVenda.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("suggestedMapping.saleDate",is("Data da Venda"))
            .body("suggestedMapping.description",is("Nome"))
            .body("suggestedMapping.quantity",is("Qtde"))
            .body("suggestedMapping.unitPriceInCents",is("Val. Unit"))
            .body("suggestedMapping.totalInCents",is("Total Venda"))
            .body("coverageFrom",is("2026-06-10"))
            .body("newRows",is(2))
            .extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data da Venda","description","Nome","quantity","Qtde","unitPriceInCents","Val. Unit","totalInCents","Total Venda","documentNumber","Cód Produto","unit","Tipo Preço"),
            "preserveColumns",List.of("Cód Produto","Nome","Tipo Preço","Val. Unit","Qtde","Total Venda")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(2)).body("rejectedRows",is(0));

        String coverageDate=given().auth().oauth2(token).when().get("/api/sales/schema?datasetId="+dataset).then().statusCode(200)
            .body("coverageFrom",notNullValue()).body("saleCount",is(2)).extract().path("coverageFrom");
        given().auth().oauth2(token).contentType("application/json").body(Map.of("text","agua","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(1))
            .body("items[0].description",containsString("AGUA COM GAS"))
            .body("items[0].location",is("Beco da Praia"))
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

        Map<String,Object> byPeriodRequest=Map.of(
            "jsonrpc","2.0",
            "id",3,
            "method","tools/call",
            "params",Map.of(
                "name","sales_quantity_by_product_in_period",
                "arguments",Map.of("datasetId",dataset,"product","agua","from",coverageDate,"to",coverageDate)
            )
        );
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(byPeriodRequest).when().post("/mcp").then().statusCode(200)
            .body("result.structuredContent.totalItems",is(1))
            .body("result.structuredContent.totalQuantity",anyOf(is(81),is("81")))
            .body("result.structuredContent.totalInCents",is(56613));
    }

    @Test void salesImportRequiresSaleDateWhenCsvProvidesDateColumn(){
        String token=adminToken(),dataset="sales-date-"+UUID.randomUUID();
        String csv="""
            Data;Nome;Qtde;Total Venda
            15/05/2026;HEINEKEN 600ML;17;340
            16/05/2026;HEINEKEN LATA;4;48
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-com-data.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("suggestedMapping.saleDate",is("Data"))
            .extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("description","Nome","quantity","Qtde","totalInCents","Total Venda")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(400)
            .body("message",containsString("Data da venda"));
    }

    @Test void salesImportPropagatesDateMarkersFromProductCodeColumn(){
        String token=adminToken(),dataset="sales-marker-"+UUID.randomUUID();
        String csv="""
            Cód Produto;Nome;Tipo Preço;Val. Unit;Qtde;Total Venda
            15/05/2026;;;;;
            30;HEINEKEN 600ML;A Vista;20;17;340
            36;HEINEKEN LATA;A Vista;12;4;48
            16/05/2026;;;;;
            30;HEINEKEN 600ML;A Vista;20;10;200
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-marker.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("suggestedMapping.saleDate",is("Data da Venda"))
            .body("totalRows",is(3))
            .extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data da Venda","description","Nome","quantity","Qtde","totalInCents","Total Venda","documentNumber","Cód Produto","unit","Tipo Preço","unitPriceInCents","Val. Unit")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(3));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-05-01","to","2026-05-15","text","heineken","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(2))
            .body("totalInCents",is(38800));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-05-16","to","2026-05-16","product","heineken"))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(1))
            .body("items[0].saleDate",is("2026-05-16"));
    }

    @Test void salesImportExtractsDateFromLocationMarkerInProductCodeColumn(){
        String token=adminToken(),dataset="sales-location-marker-"+UUID.randomUUID();
        String csv="""
            Cód Produto;Nome;Tipo Preço;Val. Unit;Qtde;Total Venda
            Beco da Praia - 01/07/2026;;;;;
            30;HEINEKEN 600ML;A Vista;20;17;340
            36;HEINEKEN LATA;A Vista;12;4;48
            Beco da Praia – 02/07/2026;;;;;
            30;HEINEKEN 600ML;A Vista;20;10;200
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-local-data.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("suggestedMapping.saleDate",is("Data da Venda"))
            .body("totalRows",is(3))
            .body("coverageFrom",is("2026-07-01"))
            .body("coverageTo",is("2026-07-02"))
            .body("rejectedRows",is(0))
            .extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data da Venda","description","Nome","quantity","Qtde","totalInCents","Total Venda","documentNumber","Cód Produto")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200)
            .body("importedRows",is(3))
            .body("rejectedRows",is(0));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-07-02","to","2026-07-02","product","heineken"))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(1))
            .body("items[0].saleDate",is("2026-07-02"));
    }

    @Test void salesImportNormalizesBecoLocationAndMcpDefaultsToBecoDaPraia(){
        String token=adminToken(),dataset="sales-beco-"+UUID.randomUUID();
        String csv="""
            Data;Produto;Local;Quantidade;Valor
            10/02/2026;HEINEKEN LATA;beco;12;144
            11/02/2026;HEINEKEN 600ML;Beco da Praia;8;160
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-beco.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","location","Local","quantity","Quantidade","totalInCents","Valor")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(2));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-02-01","to","2026-02-28","text","heineken","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(2))
            .body("items.location",everyItem(is("Beco da Praia")));

        Map<String,Object> byPeriodRequest=Map.of(
            "jsonrpc","2.0",
            "id",4,
            "method","tools/call",
            "params",Map.of(
                "name","sales_quantity_by_product_in_period",
                "arguments",Map.of("datasetId",dataset,"product","heineken","from","2026-02-01","to","2026-02-28")
            )
        );
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(byPeriodRequest).when().post("/mcp").then().statusCode(200)
            .body("result.structuredContent.totalItems",is(2))
            .body("result.structuredContent.locations",hasItem("Beco da Praia"))
            .body("result.structuredContent.totalQuantity",anyOf(is(20),is("20")));
    }

    @Test void salesImportDeduplicatesEquivalentColumnsAndKeepsSaleDateInQuery(){
        String token=adminToken(),dataset="sales-dedup-"+UUID.randomUUID();
        String csv="""
            Data;Data da Venda;Produto;Nome;Local;Qtde;Quantidade;Valor;Total Venda
            10/02/2026;10/02/2026;HEINEKEN LATA;HEINEKEN LATA;Beco da Praia;12;12;144;144
            11/02/2026;11/02/2026;HEINEKEN 600ML;HEINEKEN 600ML;Beco da Praia;8;8;160;160
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-dedup.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("headers",hasItems("Data","Produto","Local","Qtde","Valor"))
            .body("headers",not(hasItem("Data da Venda")))
            .body("headers",not(hasItem("Nome")))
            .body("headers",not(hasItem("Quantidade")))
            .body("headers",not(hasItem("Total Venda")))
            .extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","location","Local","quantity","Qtde","totalInCents","Valor"),
            "preserveColumns",List.of("Data","Produto","Local","Qtde","Valor")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(2));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-02-01","to","2026-02-28","text","heineken","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(2))
            .body("items[0].saleDate",notNullValue())
            .body("items[0].attributes",anEmptyMap());

        given().auth().oauth2(token).when().get("/api/sales/schema?datasetId="+dataset).then().statusCode(200)
            .body("fields.key",not(hasItem("data_da_venda")))
            .body("fields.key",not(hasItem("nome")))
            .body("fields.key",not(hasItem("quantidade")))
            .body("fields.key",not(hasItem("total_venda")));
    }

    @Test void salesImportIsIdempotentAcrossDailyWeeklyAndMonthlyOverlaps(){
        String token=adminToken(),dataset="sales-overlap-"+UUID.randomUUID();
        String daily="""
            Data;Produto;Local;Quantidade;Valor;Tipo
            10/03/2026;HEINEKEN LATA;Beco da Praia;12;144;A Vista
            """;
        String dailyPreview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-dia.csv","csv",daily,"datasetId",dataset))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("coverageFrom",is("2026-03-10"))
            .body("coverageTo",is("2026-03-10"))
            .body("newRows",is(1))
            .extract().asString();
        String dailyId=JsonPath.from(dailyPreview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","location","Local","quantity","Quantidade","totalInCents","Valor","unit","Tipo")
        )).when().post("/api/sales/imports/"+dailyId+"/commit").then().statusCode(200)
            .body("importedRows",is(1))
            .body("duplicateRows",is(0));

        String weekly="""
            Data da Venda;Nome;PDV;Qtde;Total Venda;Tipo Preço
            10/03/2026;HEINEKEN LATA;beco;12;144;A Vista
            11/03/2026;HEINEKEN LATA;Beco da Praia;8;96;A Vista
            """;
        String weeklyPreview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-semana.csv","csv",weekly,"datasetId",dataset))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("coverageFrom",is("2026-03-10"))
            .body("coverageTo",is("2026-03-11"))
            .body("newRows",is(1))
            .body("duplicateRows",is(1))
            .body("existingDuplicateRows",is(1))
            .extract().asString();
        String weeklyId=JsonPath.from(weeklyPreview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data da Venda","description","Nome","location","PDV","quantity","Qtde","totalInCents","Total Venda","unit","Tipo Preço")
        )).when().post("/api/sales/imports/"+weeklyId+"/commit").then().statusCode(200)
            .body("importedRows",is(1))
            .body("duplicateRows",is(1))
            .body("existingDuplicateRows",is(1));

        String monthly="""
            Data;Produto;Local;Quantidade;Valor;Tipo
            10/03/2026;HEINEKEN LATA;Beco da Praia;12;144;A Vista
            11/03/2026;HEINEKEN LATA;Beco da Praia;8;96;A Vista
            12/03/2026;HEINEKEN LATA;Beco da Praia;5;60;A Vista
            12/03/2026;HEINEKEN LATA;Beco da Praia;5;60;A Vista
            """;
        String monthlyPreview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-mes.csv","csv",monthly,"datasetId",dataset))
            .when().post("/api/sales/imports/preview").then().statusCode(200)
            .body("newRows",is(1))
            .body("duplicateRows",is(3))
            .body("existingDuplicateRows",is(2))
            .body("inFileDuplicateRows",is(1))
            .extract().asString();
        String monthlyId=JsonPath.from(monthlyPreview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","location","Local","quantity","Quantidade","totalInCents","Valor","unit","Tipo")
        )).when().post("/api/sales/imports/"+monthlyId+"/commit").then().statusCode(200)
            .body("importedRows",is(1))
            .body("duplicateRows",is(3))
            .body("existingDuplicateRows",is(2))
            .body("inFileDuplicateRows",is(1));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-03-01","to","2026-03-31","text","heineken","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(3))
            .body("totalInCents",is(30000))
            .body("totalQuantity",anyOf(is(25),is("25")));
    }

    @Test void salesProductSearchIgnoresAccentsAndSimplePlurals(){
        String token=adminToken(),dataset="sales-accent-"+UUID.randomUUID();
        String csv="""
            Data;Produto;Categoria;Local;Quantidade;Valor
            05/07/2026;CALDO PELA ÉGUA;Comidas;Beco da Praia;2;40,00
            07/07/2026;CALDO PELA ÉGUA;Comidas;Beco da Praia;4;80,00
            05/07/2026;CALDINHO DE FEIJAO;Comidas;Beco da Praia;1;18,00
            11/07/2026;CALDINHO DE FEIJAO;Comidas;Beco da Praia;3;54,00
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","vendas-accent.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","category","Categoria","location","Local","quantity","Quantidade","totalInCents","Valor")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(4));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-28","to","2026-07-28","text","egua","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(2))
            .body("totalQuantity",anyOf(is(6),is("6")));

        given().auth().oauth2(token).contentType("application/json").body(Map.of("from","2026-06-28","to","2026-07-28","text","feijão","pageSize",50))
            .when().post("/api/sales/query?datasetId="+dataset).then().statusCode(200)
            .body("totalItems",is(2))
            .body("totalQuantity",anyOf(is(4),is("4")));

        Map<String,Object> caldoRequest=Map.of(
            "jsonrpc","2.0","id",51,"method","tools/call",
            "params",Map.of(
                "name","sales_quantity_by_product_in_period",
                "arguments",Map.of("datasetId",dataset,"product","caldos pela egua","from","2026-06-28","to","2026-07-28")
            )
        );
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(caldoRequest).when().post("/mcp").then().statusCode(200)
            .body("result.structuredContent.totalItems",is(2))
            .body("result.structuredContent.totalQuantity",anyOf(is(6),is("6")))
            .body("result.structuredContent.items[0].description",containsString("CALDO PELA"));

        Map<String,Object> caldinhoRequest=Map.of(
            "jsonrpc","2.0","id",52,"method","tools/call",
            "params",Map.of(
                "name","sales_by_product",
                "arguments",Map.of("datasetId",dataset,"product","caldinho de feijão","from","2026-06-28","to","2026-07-28")
            )
        );
        given().auth().oauth2("local-purchases-token").contentType("application/json").body(caldinhoRequest).when().post("/mcp").then().statusCode(200)
            .body("result.structuredContent.totalItems",is(2))
            .body("result.structuredContent.totalQuantity",anyOf(is(4),is("4")))
            .body("result.structuredContent.items[0].description",containsString("CALDINHO DE FEIJAO"));
    }

    @Test void salesHeatmapReturnsDailyQuantitiesForYear(){
        String token=adminToken(),dataset="sales-heatmap-"+UUID.randomUUID();
        String csv="""
            Data;Produto;Categoria;Local;Quantidade;Valor
            10/01/2026;Cerveja;Bebidas;Bar;10;100,00
            10/01/2026;Água;Bebidas;Bar;5;25,00
            15/02/2026;Cerveja;Bebidas;Bar;20;200,00
            """;
        String preview=given().auth().oauth2(token).contentType("application/json").body(Map.of("fileName","heatmap.csv","csv",csv))
            .when().post("/api/sales/imports/preview").then().statusCode(200).extract().asString();
        String id=JsonPath.from(preview).getString("id");
        given().auth().oauth2(token).contentType("application/json").body(Map.of(
            "datasetId",dataset,
            "mapping",Map.of("saleDate","Data","description","Produto","category","Categoria","location","Local","quantity","Quantidade","totalInCents","Valor")
        )).when().post("/api/sales/imports/"+id+"/commit").then().statusCode(200).body("importedRows",is(3));

        given().auth().oauth2(token)
            .when().get("/api/admin/dashboard/sales-heatmap?year=2026&datasetId="+dataset)
            .then().statusCode(200)
            .body("year",is(2026))
            .body("days.date",hasItems("2026-01-10","2026-02-15"))
            .body("days.find { it.date == '2026-01-10' }.quantity",anyOf(is(15),is(15.0f),is(15.0),is("15")))
            .body("days.find { it.date == '2026-01-10' }.totalInCents",anyOf(is(12500),is(12500L)));
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
