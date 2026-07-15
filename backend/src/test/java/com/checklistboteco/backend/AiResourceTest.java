package com.checklistboteco.backend;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import com.checklistboteco.backend.ai.AiModels;
import com.checklistboteco.backend.ai.OpenAiChatService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class AiResourceTest {
    @Inject OpenAiChatService chatService;

    @Test void chatAndUsageRequireAdmin(){
        given().when().get("/api/ai/usage").then().statusCode(401);
        String token=login();
        given().header("Authorization","Bearer "+token).when().get("/api/ai/usage").then().statusCode(200).body("blocked",is(false));
        given().header("Authorization","Bearer "+token).contentType("application/json")
            .body(Map.of("messages",java.util.List.of(Map.of("role","user","text","Quanto vendeu hoje?"))))
            .when().post("/api/ai/chat").then().statusCode(503);
    }

    @Test void adminCanUpdateBudget(){
        String token=login();
        given().header("Authorization","Bearer "+token).contentType("application/json")
            .body(Map.of("monthlyLimitCents",900,"maxOutputTokens",500))
            .when().put("/api/ai/budget").then().statusCode(200).body("monthlyLimitCents",is(900)).body("maxOutputTokens",is(500));
    }

    @Test void salesQuestionAboutHeinekenInMarchAllowsPeriodTool(){
        AiModels.ChatMessage message=new AiModels.ChatMessage();
        message.role="user";
        message.text="Quantas Heinekens vendemos em março de 2026?";
        AiModels.ChatRequest request=new AiModels.ChatRequest();
        request.messages=List.of(message);
        assertThat(chatService.allowedToolsForTesting(request),hasItem("sales_quantity_by_product_in_period"));
    }

    @Test void sellerSalesQuestionAllowsSellerTool(){
        AiModels.ChatMessage message=new AiModels.ChatMessage();
        message.role="user";
        message.text="Quanto o João Rodrigues vendeu ontem no forró e quanto deu de 10%?";
        AiModels.ChatRequest request=new AiModels.ChatRequest();
        request.messages=List.of(message);
        assertThat(chatService.allowedToolsForTesting(request),hasItem("sales_by_seller"));
    }

    private String login(){
        String first=given().contentType("application/json").body("""
            {"email":"admin@checklistboteco.com","password":"admin123","deviceId":"ai-tests","deviceName":"AI Tests"}
            """).when().post("/api/auth/login").then().statusCode(200).extract().asString();
        var json=JsonPath.from(first);
        if(!json.getBoolean("requiresTwoFactor")) return json.getString("token");
        return given().contentType("application/json").body(Map.of("challengeId",json.getString("challengeId"),"code",json.getString("developmentCode"),"deviceId","ai-tests","deviceName","AI Tests"))
            .when().post("/api/auth/verify-device").then().statusCode(200).extract().path("token");
    }
}
