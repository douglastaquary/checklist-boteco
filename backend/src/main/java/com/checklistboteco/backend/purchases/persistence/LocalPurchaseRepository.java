package com.checklistboteco.backend.purchases.persistence;

import com.checklistboteco.backend.purchases.domain.PurchaseModels.ImportBatch;
import com.checklistboteco.backend.purchases.domain.PurchaseModels.Purchase;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@UnlessBuildProfile("prod")
public class LocalPurchaseRepository implements PurchaseRepository {
    protected final Map<String,ImportBatch> imports=new ConcurrentHashMap<>();
    protected final Map<String,Purchase> purchases=new ConcurrentHashMap<>();
    protected final Set<String> rowHashes=ConcurrentHashMap.newKeySet();

    public void saveBatch(ImportBatch batch){ imports.put(batch.id,batch); }
    public ImportBatch getBatch(String id){ return imports.get(id); }
    public List<ImportBatch> batches(){ return imports.values().stream().sorted(Comparator.comparing((ImportBatch b)->b.createdAt).reversed()).toList(); }
    public synchronized boolean saveIfAbsent(Purchase purchase){
        String key=purchase.datasetId+":"+purchase.rowHash;
        if(!rowHashes.add(key)) return false;
        purchases.put(purchase.id,purchase); return true;
    }
    public List<Purchase> purchases(String datasetId){
        return purchases.values().stream().filter(p->Objects.equals(datasetId,p.datasetId)).toList();
    }
}
