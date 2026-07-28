package com.checklistboteco.backend.ai;

import com.checklistboteco.backend.ai.AiModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.LaunchMode;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@ApplicationScoped
public class AiUsageService {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="ai.usage.local-file", defaultValue=".data/ai-usage-local.json") String localFile;
    @ConfigProperty(name="ai.budget.monthly-limit-cents", defaultValue="500") long defaultLimit;
    @ConfigProperty(name="ai.budget.max-output-tokens", defaultValue="1600") int defaultMaxOutput;
    @ConfigProperty(name="checklist.dynamodb.table") String table;
    @ConfigProperty(name="checklist.aws.region") String region;
    private final List<AuditRecord> records = new ArrayList<>();
    private Budget budget;
    private DynamoDbClient dynamo;

    @PostConstruct synchronized void load() {
        budget = new Budget(); budget.monthlyLimitCents=defaultLimit; budget.maxOutputTokens=defaultMaxOutput;
        try {
            if(LaunchMode.current()==LaunchMode.NORMAL) {
                dynamo=DynamoDbClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create()).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
                dynamo.scan(ScanRequest.builder().tableName(table).filterExpression("begins_with(pk, :usage) OR pk = :budget").expressionAttributeValues(Map.of(":usage",AttributeValue.fromS("AI_USAGE#"),":budget",AttributeValue.fromS("AI_BUDGET#BECO"))).build()).items().forEach(this::hydrate);
                return;
            }
            Path path=Path.of(localFile);
            if(Files.exists(path)) {
                Persisted value=mapper.readValue(path.toFile(),Persisted.class);
                if(value.records!=null) records.addAll(value.records);
                if(value.budget!=null) budget=value.budget;
            }
        } catch(Exception ignored) { }
    }
    @PreDestroy void close(){ if(dynamo!=null)dynamo.close(); }
    public synchronized Budget budget(){ return budget; }
    public synchronized UsageSummary summary(String month){
        String selected=month==null||month.isBlank()?YearMonth.now().toString():YearMonth.parse(month).toString();
        UsageSummary result=new UsageSummary(); result.month=selected; result.monthlyLimitCents=budget.monthlyLimitCents;
        records.stream().filter(v->selected.equals(v.month)).forEach(v->{ result.requests++; result.inputTokens+=v.inputTokens; result.cachedInputTokens+=v.cachedInputTokens; result.outputTokens+=v.outputTokens; result.estimatedCostMicros+=v.estimatedCostMicros; });
        result.blocked=result.estimatedCostMicros>=result.monthlyLimitCents*10_000L; return result;
    }
    public synchronized void record(AuditRecord value){ records.add(value); if(dynamo!=null) put("AI_USAGE",value.id,value); else persist(); }
    public synchronized Budget update(BudgetUpdate request){
        if(request==null||request.monthlyLimitCents<1||request.maxOutputTokens<64||request.maxOutputTokens>4000) throw new IllegalArgumentException("Orçamento ou limite de resposta inválido");
        budget=new Budget(); budget.monthlyLimitCents=request.monthlyLimitCents; budget.maxOutputTokens=request.maxOutputTokens; if(dynamo!=null) put("AI_BUDGET","BECO",budget); else persist(); return budget;
    }
    private void persist(){
        try { Path path=Path.of(localFile); if(path.getParent()!=null) Files.createDirectories(path.getParent()); Persisted value=new Persisted(); value.records=records; value.budget=budget; mapper.writeValue(path.toFile(),value); } catch(Exception e){ throw new IllegalStateException("Falha ao persistir métricas de IA",e); }
    }
    public static class Persisted { public List<AuditRecord> records=new ArrayList<>(); public Budget budget; }
    private void put(String kind,String id,Object value){
        try { dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of("pk",AttributeValue.fromS(kind+"#"+id),"kind",AttributeValue.fromS(kind),"payload",AttributeValue.fromS(mapper.writeValueAsString(value)))).build()); }
        catch(Exception e){ throw new IllegalStateException("Falha ao persistir métricas de IA no DynamoDB",e); }
    }
    private void hydrate(Map<String,AttributeValue> item){
        try { String kind=item.get("kind").s(); if("AI_USAGE".equals(kind)) records.add(mapper.readValue(item.get("payload").s(),AuditRecord.class)); else if("AI_BUDGET".equals(kind)) budget=mapper.readValue(item.get("payload").s(),Budget.class); }
        catch(Exception e){ throw new IllegalStateException("Métrica de IA inválida no DynamoDB",e); }
    }
}
