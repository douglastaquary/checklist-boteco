package com.checklistboteco.backend.inventory.persistence;

import com.checklistboteco.backend.inventory.domain.InventoryModels.CountSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@ApplicationScoped @IfBuildProfile("prod")
public class DynamoInventoryRepository implements InventoryRepository {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="checklist.dynamodb.table") String table;
    @ConfigProperty(name="checklist.aws.region") String region;
    private final Map<String,CountSession> sessions=new ConcurrentHashMap<>();
    private DynamoDbClient dynamo;
    @PostConstruct void connect(){ dynamo=DynamoDbClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create()).httpClientBuilder(UrlConnectionHttpClient.builder()).build(); dynamo.scan(ScanRequest.builder().tableName(table).filterExpression("kind = :kind").expressionAttributeValues(Map.of(":kind",AttributeValue.fromS("INVENTORY_COUNT"))).build()).items().forEach(this::hydrate); }
    @PreDestroy void close(){ if(dynamo!=null)dynamo.close(); }
    public void save(CountSession value){ sessions.put(value.id,value); put(value); }
    public List<CountSession> list(){ return sessions.values().stream().sorted(Comparator.comparing((CountSession value)->value.countDate).reversed()).toList(); }
    public void delete(String id){ if(sessions.remove(id)==null) throw new IllegalArgumentException("Contagem não encontrada"); dynamo.deleteItem(DeleteItemRequest.builder().tableName(table).key(Map.of("pk",AttributeValue.fromS("INVENTORY#"+id))).build()); }
    private void put(CountSession value){ try { dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of("pk",AttributeValue.fromS("INVENTORY#"+value.id),"kind",AttributeValue.fromS("INVENTORY_COUNT"),"payload",AttributeValue.fromS(mapper.writeValueAsString(value)))).build()); } catch(Exception e){ throw new IllegalStateException("Falha ao persistir contagem no DynamoDB",e); } }
    private void hydrate(Map<String,AttributeValue> item){ try { CountSession value=mapper.readValue(item.get("payload").s(),CountSession.class); sessions.put(value.id,value); } catch(Exception e){ throw new IllegalStateException("Contagem inválida no DynamoDB",e); } }
}
