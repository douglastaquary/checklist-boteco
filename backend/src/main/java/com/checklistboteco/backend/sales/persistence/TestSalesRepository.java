package com.checklistboteco.backend.sales.persistence;

import com.checklistboteco.backend.sales.domain.SalesModels.ImportBatch;
import com.checklistboteco.backend.sales.domain.SalesModels.Sale;
import com.checklistboteco.backend.sales.domain.SalesFingerprint;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@IfBuildProfile("test")
public class TestSalesRepository implements SalesRepository {
    protected final Map<String,ImportBatch> imports=new ConcurrentHashMap<>();
    protected final Map<String,Sale> sales=new ConcurrentHashMap<>();
    protected final Set<String> rowHashes=ConcurrentHashMap.newKeySet();

    public void saveBatch(ImportBatch batch){ imports.put(batch.id,batch); }
    public ImportBatch getBatch(String id){ return imports.get(id); }
    public List<ImportBatch> batches(){ return imports.values().stream().sorted(Comparator.comparing((ImportBatch b)->b.createdAt).reversed()).toList(); }
    public boolean existsFingerprint(String datasetId,String fingerprint){ return fingerprint!=null&&!fingerprint.isBlank()&&rowHashes.contains(datasetId+":"+fingerprint); }
    public synchronized boolean saveIfAbsent(Sale sale){
        String key=sale.datasetId+":"+SalesFingerprint.existingOrComputed(sale);
        if(!rowHashes.add(key)) return false;
        if(sale.rowHash!=null&&!sale.rowHash.isBlank()) rowHashes.add(sale.datasetId+":legacy:"+sale.rowHash);
        sales.put(sale.id,sale); return true;
    }
    public List<Sale> sales(String datasetId){ return sales.values().stream().filter(value->Objects.equals(datasetId,value.datasetId)).toList(); }
}
