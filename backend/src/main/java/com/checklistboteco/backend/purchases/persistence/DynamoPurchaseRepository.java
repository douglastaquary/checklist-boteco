package com.checklistboteco.backend.purchases.persistence;

import com.checklistboteco.backend.purchases.domain.PurchaseModels.ImportBatch;
import com.checklistboteco.backend.purchases.domain.PurchaseModels.Purchase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@IfBuildProfile("prod")
public class DynamoPurchaseRepository implements PurchaseRepository {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="purchases.dynamodb.table") String table;
    @ConfigProperty(name="checklist.aws.region") String region;
    private final Map<String,ImportBatch> imports=new ConcurrentHashMap<>();
    private final Map<String,Purchase> purchases=new ConcurrentHashMap<>();
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
    public synchronized boolean saveIfAbsent(Purchase purchase){
        String hashKey=purchase.datasetId+":"+purchase.rowHash;
        if(!rowHashes.add(hashKey)) return false;
        purchases.put(purchase.id,purchase);
        put("DATASET#"+purchase.datasetId,"PURCHASE#"+(purchase.purchaseDate==null?"UNDATED":purchase.purchaseDate)+"#"+purchase.id,"PURCHASE",purchase);
        return true;
    }
    public List<Purchase> purchases(String datasetId){ return purchases.values().stream().filter(p->Objects.equals(datasetId,p.datasetId)).toList(); }

    private void put(String pk,String sk,String kind,Object value){
        try {
            dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
                "pk",AttributeValue.fromS(pk),"sk",AttributeValue.fromS(sk),"kind",AttributeValue.fromS(kind),
                "payload",AttributeValue.fromS(mapper.writeValueAsString(value))
            )).build());
        } catch(Exception e){ throw new IllegalStateException("Falha ao gravar compras no DynamoDB",e); }
    }
    private void hydrate(Map<String,AttributeValue> item){
        try {
            String kind=item.get("kind").s(),payload=item.get("payload").s();
            if("IMPORT".equals(kind)){ ImportBatch b=mapper.readValue(payload,ImportBatch.class); imports.put(b.id,b); }
            if("PURCHASE".equals(kind)){ Purchase p=mapper.readValue(payload,Purchase.class); purchases.put(p.id,p); rowHashes.add(p.datasetId+":"+p.rowHash); }
        } catch(Exception e){ throw new IllegalStateException("Item de compras inválido no DynamoDB",e); }
    }
}
