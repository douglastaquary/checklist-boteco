package com.checklistboteco.backend.purchases.persistence;

import com.checklistboteco.backend.purchases.domain.PurchaseModels.ImportBatch;
import com.checklistboteco.backend.purchases.domain.PurchaseModels.Purchase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@IfBuildProfile("dev")
public class LocalPurchaseRepository implements PurchaseRepository {
    static final class Snapshot {
        public List<ImportBatch> imports=new ArrayList<>();
        public List<Purchase> purchases=new ArrayList<>();
    }
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="purchases.local.file") String fileName;
    protected final Map<String,ImportBatch> imports=new ConcurrentHashMap<>();
    protected final Map<String,Purchase> purchases=new ConcurrentHashMap<>();
    protected final Set<String> rowHashes=ConcurrentHashMap.newKeySet();
    private Path file;

    @PostConstruct void load(){
        file=Path.of(fileName).toAbsolutePath().normalize();
        if(!Files.exists(file)) return;
        try {
            Snapshot snapshot=mapper.readValue(Files.readString(file),Snapshot.class);
            snapshot.imports.forEach(batch->imports.put(batch.id,batch));
            snapshot.purchases.forEach(purchase->{ purchases.put(purchase.id,purchase); rowHashes.add(purchase.datasetId+":"+purchase.rowHash); });
        } catch(Exception e){ throw new IllegalStateException("Falha ao carregar compras locais persistidas",e); }
    }

    public synchronized void saveBatch(ImportBatch batch){ imports.put(batch.id,batch); persist(); }
    public ImportBatch getBatch(String id){ return imports.get(id); }
    public List<ImportBatch> batches(){ return imports.values().stream().sorted(Comparator.comparing((ImportBatch b)->b.createdAt).reversed()).toList(); }
    public synchronized boolean saveIfAbsent(Purchase purchase){
        String key=purchase.datasetId+":"+purchase.rowHash;
        if(!rowHashes.add(key)) return false;
        purchases.put(purchase.id,purchase); persist(); return true;
    }
    public List<Purchase> purchases(String datasetId){
        return purchases.values().stream().filter(p->Objects.equals(datasetId,p.datasetId)).toList();
    }

    private void persist(){
        try {
            Files.createDirectories(file.getParent());
            Snapshot snapshot=new Snapshot();
            snapshot.imports=new ArrayList<>(imports.values());
            snapshot.purchases=new ArrayList<>(purchases.values());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),snapshot);
        } catch(Exception e){ throw new IllegalStateException("Falha ao persistir compras locais",e); }
    }
}
