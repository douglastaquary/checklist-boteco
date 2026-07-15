package com.checklistboteco.backend;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class WorkClockResourceTest {
    @Test
    void mcpExposesReadOnlyWorkClockTools() {
        given().header("Authorization", "Bearer local-purchases-token").contentType("application/json")
            .body(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list", "params", Map.of()))
            .post("/mcp").then().statusCode(200)
            .body("result.tools.name", hasItems(
                "work_clock_summary",
                "work_clock_absences",
                "work_clock_entries",
                "work_clock_schedule",
                "work_clock_worksite"
            ));
    }

    @Test
    void mcpReturnsWorkClockSummaryAndEntries() {
        String admin = login("admin@checklistboteco.com", "admin123");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "mcp-ponto+" + suffix + "@teste.com";
        String created = given().header("Authorization", "Bearer " + admin).contentType("application/json").body(Map.of(
            "name", "MCP Ponto " + suffix,
            "email", email,
            "password", "senha123",
            "workSector", "BARMAN",
            "permissionLevel", "USER"
        )).post("/api/users").then().statusCode(201).extract().asString();
        String userId = JsonPath.from(created).getString("id");
        String token = login(email, "senha123");
        long entrada = LocalDate.of(2026, 6, 18).atTime(8, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();
        long saida = LocalDate.of(2026, 6, 18).atTime(17, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();

        given().header("Authorization", "Bearer " + token).contentType("application/json").body(Map.of(
            "workClockEntries", List.of(
                workClockEntry(userId, "ENTRADA", entrada),
                workClockEntry(userId, "SAIDA", saida)
            )
        )).post("/api/sync/push").then().statusCode(200);

        given().header("Authorization", "Bearer local-purchases-token").contentType("application/json")
            .body(Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/call",
                "params", Map.of(
                    "name", "work_clock_summary",
                    "arguments", Map.of("from", "2026-06-18", "to", "2026-06-18", "userId", userId)
                )
            ))
            .post("/mcp").then().statusCode(200)
            .body("result.structuredContent[0].userId", is(userId))
            .body("result.structuredContent[0].workedHours", greaterThan(0f));

        given().header("Authorization", "Bearer local-purchases-token").contentType("application/json")
            .body(Map.of(
                "jsonrpc", "2.0",
                "id", 3,
                "method", "tools/call",
                "params", Map.of(
                    "name", "work_clock_entries",
                    "arguments", Map.of("userId", userId, "from", "2026-06-18", "to", "2026-06-18")
                )
            ))
            .post("/mcp").then().statusCode(200)
            .body("result.structuredContent.size()", is(2));

        given().header("Authorization", "Bearer " + admin).delete("/api/users/" + userId).then().statusCode(204);
    }

    @Test
    void adminCanSummarizeScheduleAndExportWorkClock() {
        String admin = login("admin@checklistboteco.com", "admin123");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "ponto+" + suffix + "@teste.com";
        String created = given().header("Authorization", "Bearer " + admin).contentType("application/json").body(Map.of(
            "name", "Ponto " + suffix,
            "email", email,
            "password", "senha123",
            "workSector", "BARMAN",
            "permissionLevel", "USER"
        )).post("/api/users").then().statusCode(201).extract().asString();
        String userId = JsonPath.from(created).getString("id");
        String token = login(email, "senha123");
        long entrada = LocalDate.of(2026, 6, 16).atTime(8, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();
        long saida = LocalDate.of(2026, 6, 16).atTime(17, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();

        given().header("Authorization", "Bearer " + token).contentType("application/json").body(Map.of(
            "workClockEntries", List.of(
                workClockEntry(userId, "ENTRADA", entrada),
                workClockEntry(userId, "SAIDA", saida)
            )
        )).post("/api/sync/push").then().statusCode(200);

        given().header("Authorization", "Bearer " + admin).contentType("application/json").body(Map.of(
            "workingDaysOfWeek", List.of(1, 2, 3, 4),
            "workDateExceptions", List.of(),
            "offDateExceptions", List.of()
        )).put("/api/work-clock/schedule/" + userId).then().statusCode(200)
            .body("workingDaysOfWeek", hasSize(4));

        given().header("Authorization", "Bearer " + admin)
            .queryParam("from", "2026-06-16")
            .queryParam("to", "2026-06-17")
            .queryParam("userId", userId)
            .get("/api/work-clock/summary").then().statusCode(200)
            .body("[0].userId", is(userId))
            .body("[0].workedHours", greaterThan(0f))
            .body("[0].absenceDays", is(1))
            .body("[0].absenceDates", hasItem("2026-06-17"))
            .body("[0].absenceDetails[0].reason", is("Sem entrada registrada"));

        given().header("Authorization", "Bearer " + token)
            .queryParam("from", "2026-06-16")
            .queryParam("to", "2026-06-17")
            .get("/api/work-clock/me/summary").then().statusCode(200)
            .body("userId", is(userId))
            .body("absenceDays", is(1))
            .body("absenceDates", hasItem("2026-06-17"));

        given().header("Authorization", "Bearer " + admin)
            .queryParam("userId", userId)
            .queryParam("from", "2026-06-16")
            .queryParam("to", "2026-06-16")
            .get("/api/work-clock/entries").then().statusCode(200)
            .body("size()", is(2));

        given().header("Authorization", "Bearer " + admin)
            .queryParam("year", 2026)
            .queryParam("month", 6)
            .get("/api/work-clock/export.csv").then().statusCode(200)
            .header("Content-Disposition", containsString("ponto-2026-6.csv"))
            .body(containsString("Dias de falta"))
            .body(containsString("2026-06-17"));

        given().header("Authorization", "Bearer " + admin)
            .queryParam("year", 2026)
            .queryParam("month", 6)
            .get("/api/work-clock/export.pdf").then().statusCode(200)
            .contentType("application/pdf");

        given().header("Authorization", "Bearer local-purchases-token").contentType("application/json")
            .body(Map.of(
                "jsonrpc", "2.0",
                "id", 4,
                "method", "tools/call",
                "params", Map.of(
                    "name", "work_clock_absences",
                    "arguments", Map.of("from", "2026-06-16", "to", "2026-06-17", "userId", userId)
                )
            ))
            .post("/mcp").then().statusCode(200)
            .body("result.structuredContent[0].absenceDays", is(1))
            .body("result.structuredContent[0].absenceDates", hasItem("2026-06-17"));

        given().header("Authorization", "Bearer " + admin).delete("/api/users/" + userId).then().statusCode(204);
    }

    @Test
    void syncRejectsWorkClockEntryFromAnotherUser() {
        String admin = login("admin@checklistboteco.com", "admin123");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "ponto-outro+" + suffix + "@teste.com";
        String created = given().header("Authorization", "Bearer " + admin).contentType("application/json").body(Map.of(
            "name", "Ponto Outro " + suffix,
            "email", email,
            "password", "senha123",
            "workSector", "BARMAN",
            "permissionLevel", "USER"
        )).post("/api/users").then().statusCode(201).extract().asString();
        String userId = JsonPath.from(created).getString("id");
        String token = login(email, "senha123");
        long entrada = LocalDate.of(2026, 6, 19).atTime(8, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();

        given().header("Authorization", "Bearer " + token).contentType("application/json").body(Map.of(
            "workClockEntries", List.of(workClockEntry("outro-usuario", "ENTRADA", entrada))
        )).post("/api/sync/push").then().statusCode(403)
            .body("message", containsString("outro usuário"));

        given().header("Authorization", "Bearer " + admin).delete("/api/users/" + userId).then().statusCode(204);
    }

    @Test
    void syncRejectsWorkClockEntryOutsideWorksiteRadius() {
        String admin = login("admin@checklistboteco.com", "admin123");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "ponto-raio+" + suffix + "@teste.com";
        String created = given().header("Authorization", "Bearer " + admin).contentType("application/json").body(Map.of(
            "name", "Ponto Raio " + suffix,
            "email", email,
            "password", "senha123",
            "workSector", "BARMAN",
            "permissionLevel", "USER"
        )).post("/api/users").then().statusCode(201).extract().asString();
        String userId = JsonPath.from(created).getString("id");
        String token = login(email, "senha123");
        long entrada = LocalDate.of(2026, 6, 20).atTime(8, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli();

        given().header("Authorization", "Bearer " + token).contentType("application/json").body(Map.of(
            "workClockEntries", List.of(workClockEntry(userId, "ENTRADA", entrada, 25.0))
        )).post("/api/sync/push").then().statusCode(400)
            .body("message", containsString("fora do raio"));

        given().header("Authorization", "Bearer " + admin).delete("/api/users/" + userId).then().statusCode(204);
    }

    private Map<String, Object> workClockEntry(String userId, String type, long registeredAt) {
        return workClockEntry(userId, type, registeredAt, 2.0);
    }

    private Map<String, Object> workClockEntry(String userId, String type, long registeredAt, double distanceFromWorkMeters) {
        return Map.of(
            "id", userId + "-" + type + "-" + registeredAt,
            "userId", userId,
            "type", type,
            "registeredAt", registeredAt,
            "latitude", -23.85491,
            "longitude", -46.13872,
            "distanceFromWorkMeters", distanceFromWorkMeters,
            "isLate", false,
            "createdAt", registeredAt,
            "updatedAt", registeredAt
        );
    }

    private String login(String email, String password) {
        String device = "workclock-" + UUID.randomUUID();
        String first = given().contentType("application/json").body(Map.of(
            "email", email,
            "password", password,
            "deviceId", device,
            "deviceName", "JUnit"
        )).post("/api/auth/login").then().statusCode(200).extract().asString();
        JsonPath json = JsonPath.from(first);
        return given().contentType("application/json").body(Map.of(
            "challengeId", json.getString("challengeId"),
            "code", json.getString("developmentCode"),
            "deviceId", device,
            "deviceName", "JUnit"
        )).post("/api/auth/verify-device").then().statusCode(200).extract().path("token");
    }
}
