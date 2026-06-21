package com.checklistboteco.backend.inventory.persistence;

import com.checklistboteco.backend.inventory.domain.InventoryModels.AdminStockBalance;
import com.checklistboteco.backend.inventory.domain.InventoryModels.AdminStockSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@ApplicationScoped
@IfBuildProfile("prod")
public class DynamoAdminStockRepository implements AdminStockRepository {
    private static final String PK = "ADMIN_STOCK#SNAPSHOT";

    private static final class Snapshot {
        public List<AdminStockSession> sessions = new ArrayList<>();
        public List<AdminStockBalance> balances = new ArrayList<>();
        public Map<String, String> appliedAudits = new LinkedHashMap<>();
    }

    @Inject ObjectMapper mapper;
    @ConfigProperty(name = "checklist.dynamodb.table") String table;
    @ConfigProperty(name = "checklist.aws.region") String region;
    private final Map<String, AdminStockSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, AdminStockBalance> balances = new ConcurrentHashMap<>();
    private final Map<String, String> appliedAudits = new ConcurrentHashMap<>();
    private DynamoDbClient dynamo;

    @PostConstruct
    void connect() {
        dynamo = DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
        GetItemResponse response = dynamo.getItem(GetItemRequest.builder()
            .tableName(table)
            .key(Map.of("pk", AttributeValue.fromS(PK)))
            .build());
        if (response.item() == null || !response.item().containsKey("payload")) return;
        try {
            Snapshot snapshot = mapper.readValue(response.item().get("payload").s(), Snapshot.class);
            snapshot.sessions.forEach(value -> sessions.put(value.id, value));
            snapshot.balances.forEach(value -> balances.put(balanceKey(value.productKey, value.location), value));
            if (snapshot.appliedAudits != null) appliedAudits.putAll(snapshot.appliedAudits);
        } catch (Exception error) {
            throw new IllegalStateException("Falha ao carregar estoque administrativo no DynamoDB", error);
        }
    }

    @PreDestroy
    void close() {
        if (dynamo != null) dynamo.close();
    }

    @Override
    public synchronized void saveSession(AdminStockSession session) {
        sessions.put(session.id, session);
        persist();
    }

    @Override
    public List<AdminStockSession> listSessions() {
        return sessions.values().stream()
            .sorted(Comparator.comparing((AdminStockSession value) -> value.countDate).reversed()
                .thenComparing(value -> value.submittedAt, Comparator.reverseOrder()))
            .toList();
    }

    @Override
    public List<AdminStockBalance> listBalances() {
        return balances.values().stream()
            .sorted(Comparator.comparing(value -> value.productName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Override
    public synchronized AdminStockBalance adjustBalance(String productKey, String productName, String location, BigDecimal delta) {
        String key = balanceKey(productKey, location);
        AdminStockBalance current = balances.get(key);
        if (current == null) {
            current = new AdminStockBalance();
            current.productKey = productKey;
            current.productName = productName;
            current.location = location;
        }
        current.quantity = current.quantity.add(delta);
        current.updatedAt = Instant.now();
        balances.put(key, current);
        persist();
        return current;
    }

    @Override
    public Optional<Instant> appliedAuditAt(LocalDate date, String location) {
        String marker = appliedAudits.get(appliedAuditKey(date, location));
        if (marker == null || marker.isBlank()) return Optional.empty();
        return Optional.of(Instant.parse(marker.split("\\|")[0]));
    }

    @Override
    public synchronized void markAuditApplied(LocalDate date, String location, Instant appliedAt, String appliedBy) {
        appliedAudits.put(appliedAuditKey(date, location), appliedAt.toString() + "|" + appliedBy);
        persist();
    }

    private void persist() {
        try {
            Snapshot snapshot = new Snapshot();
            snapshot.sessions = new ArrayList<>(sessions.values());
            snapshot.balances = new ArrayList<>(balances.values());
            snapshot.appliedAudits = new LinkedHashMap<>(appliedAudits);
            dynamo.putItem(PutItemRequest.builder()
                .tableName(table)
                .item(Map.of(
                    "pk", AttributeValue.fromS(PK),
                    "kind", AttributeValue.fromS("ADMIN_STOCK"),
                    "payload", AttributeValue.fromS(mapper.writeValueAsString(snapshot))
                ))
                .build());
        } catch (Exception error) {
            throw new IllegalStateException("Falha ao persistir estoque administrativo no DynamoDB", error);
        }
    }

    private static String balanceKey(String productKey, String location) {
        return productKey + "|" + location;
    }

    private static String appliedAuditKey(LocalDate date, String location) {
        return date + "|" + location;
    }
}
