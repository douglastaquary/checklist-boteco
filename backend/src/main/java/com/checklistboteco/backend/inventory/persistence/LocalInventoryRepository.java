package com.checklistboteco.backend.inventory.persistence;

import com.checklistboteco.backend.inventory.domain.InventoryModels.CountSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@UnlessBuildProfile("prod")
public class LocalInventoryRepository implements InventoryRepository {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="inventory.local.file") String fileName;
    private final Map<String,CountSession> sessions=new ConcurrentHashMap<>();
    private Path file;
    @PostConstruct void load(){
        file=Path.of(fileName).toAbsolutePath().normalize();
        if(!Files.exists(file)) return;
        try { List<CountSession> values=mapper.readValue(Files.readString(file),mapper.getTypeFactory().constructCollectionType(List.class,CountSession.class)); values.forEach(value->sessions.put(value.id,value)); }
        catch(Exception e){ throw new IllegalStateException("Falha ao carregar contagens locais",e); }
    }
    public synchronized void save(CountSession value){ sessions.put(value.id,value); persist(); }
    public List<CountSession> list(){ return sessions.values().stream().sorted(Comparator.comparing((CountSession value)->value.countDate).reversed().thenComparing(value->value.submittedAt,Comparator.reverseOrder())).toList(); }
    public synchronized void delete(String id){ if(sessions.remove(id)==null) throw new IllegalArgumentException("Contagem não encontrada"); persist(); }
    private void persist(){ try { if(file.getParent()!=null) Files.createDirectories(file.getParent()); mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),sessions.values()); } catch(Exception e){ throw new IllegalStateException("Falha ao persistir contagens",e); } }
}
