package com.checklistboteco.backend.sales.persistence;

import com.checklistboteco.backend.sales.domain.SalesModels.ImportBatch;
import com.checklistboteco.backend.sales.domain.SalesModels.Sale;
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

@ApplicationScoped
@IfBuildProfile("prod")
public class DynamoSalesRepository implements SalesRepository {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="sales.dynamodb.table") String table;
    @ConfigProperty(name="checklist.aws.region") String region;
    private final Map<String,ImportBatch> imports=new ConcurrentHashMap<>();
    private final Map<String,Sale> sales=new ConcurrentHashMap<>();
    private final Set<String> rowHashes=ConcurrentHashMap.newKeySet();
    private DynamoDbClient dynamo;

    @PostConstruct void connect(){
        dynamo=DynamoDbClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create())
            .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        dynamo.scan(ScanRequest.builder().tableName(table).build()).items().forEach(this::hydrate);
    }
    @PreDestroy void close(){ if(dynamo!=null)dynamo.close(); }

    public void saveBatch(ImportBatch batch){ imports.put(batch.id,batch); put("IMPORT#"+batch.id,"META","IMPORT",batch); }
    public ImportBatch getBatch(String id){ return imports.get(id); }
    public List<ImportBatch> batches(){ return imports.values().stream().sorted(Comparator.comparing((ImportBatch b)->b.createdAt).reversed()).toList(); }
    public synchronized boolean saveIfAbsent(Sale sale){
        String hashKey=sale.datasetId+":"+sale.rowHash;
        if(!rowHashes.add(hashKey)) return false;
        sales.put(sale.id,sale);
        put("DATASET#"+sale.datasetId,"SALE#"+(sale.saleDate==null?"UNDATED":sale.saleDate)+"#"+sale.id,"SALE",sale);
        return true;
    }
    public List<Sale> sales(String datasetId){ return sales.values().stream().filter(value->Objects.equals(datasetId,value.datasetId)).toList(); }

    private void put(String pk,String sk,String kind,Object value){
        try {
            dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
                "pk",AttributeValue.fromS(pk),"sk",AttributeValue.fromS(sk),"kind",AttributeValue.fromS(kind),
                "payload",AttributeValue.fromS(mapper.writeValueAsString(value))
            )).build());
        } catch(Exception e){ throw new IllegalStateException("Falha ao gravar vendas no DynamoDB",e); }
    }
    private void hydrate(Map<String,AttributeValue> item){
        try {
            String kind=item.get("kind").s(),payload=item.get("payload").s();
            if("IMPORT".equals(kind)){ ImportBatch batch=mapper.readValue(payload,ImportBatch.class); imports.put(batch.id,batch); }
            if("SALE".equals(kind)){ Sale sale=mapper.readValue(payload,Sale.class); sales.put(sale.id,sale); rowHashes.add(sale.datasetId+":"+sale.rowHash); }
        } catch(Exception e){ throw new IllegalStateException("Item de vendas inválido no DynamoDB",e); }
    }
}
