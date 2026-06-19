package com.checklistboteco.backend.sales.persistence;

import com.checklistboteco.backend.sales.domain.SalesModels.ImportBatch;
import com.checklistboteco.backend.sales.domain.SalesModels.Sale;
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
public class LocalSalesRepository implements SalesRepository {
    static final class Snapshot {
        public List<ImportBatch> imports=new ArrayList<>();
        public List<Sale> sales=new ArrayList<>();
    }
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="sales.local.file") String fileName;
    protected final Map<String,ImportBatch> imports=new ConcurrentHashMap<>();
    protected final Map<String,Sale> sales=new ConcurrentHashMap<>();
    protected final Set<String> rowHashes=ConcurrentHashMap.newKeySet();
    private Path file;

    @PostConstruct void load(){
        file=Path.of(fileName).toAbsolutePath().normalize();
        if(!Files.exists(file)) return;
        try {
            Snapshot snapshot=mapper.readValue(Files.readString(file),Snapshot.class);
            snapshot.imports.forEach(batch->imports.put(batch.id,batch));
            snapshot.sales.forEach(sale->{ sales.put(sale.id,sale); rowHashes.add(sale.datasetId+":"+sale.rowHash); });
        } catch(Exception e){ throw new IllegalStateException("Falha ao carregar vendas locais persistidas",e); }
    }

    public synchronized void saveBatch(ImportBatch batch){ imports.put(batch.id,batch); persist(); }
    public ImportBatch getBatch(String id){ return imports.get(id); }
    public List<ImportBatch> batches(){ return imports.values().stream().sorted(Comparator.comparing((ImportBatch b)->b.createdAt).reversed()).toList(); }
    public synchronized boolean saveIfAbsent(Sale sale){
        String key=sale.datasetId+":"+sale.rowHash;
        if(!rowHashes.add(key)) return false;
        sales.put(sale.id,sale); persist(); return true;
    }
    public List<Sale> sales(String datasetId){
        return sales.values().stream().filter(value->Objects.equals(datasetId,value.datasetId)).toList();
    }

    private void persist(){
        try {
            Files.createDirectories(file.getParent());
            Snapshot snapshot=new Snapshot();
            snapshot.imports=new ArrayList<>(imports.values());
            snapshot.sales=new ArrayList<>(sales.values());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),snapshot);
        } catch(Exception e){ throw new IllegalStateException("Falha ao persistir vendas locais",e); }
    }
}
