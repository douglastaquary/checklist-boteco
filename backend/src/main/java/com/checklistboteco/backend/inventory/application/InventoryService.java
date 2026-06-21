package com.checklistboteco.backend.inventory.application;

import static com.checklistboteco.backend.inventory.domain.InventoryModels.*;

import com.checklistboteco.backend.inventory.persistence.InventoryRepository;
import com.checklistboteco.backend.model.Models.User;
import com.checklistboteco.backend.sales.domain.SalesModels.Sale;
import com.checklistboteco.backend.sales.persistence.SalesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@ApplicationScoped
public class InventoryService {
    @Inject InventoryRepository repository;
    @Inject com.checklistboteco.backend.inventory.persistence.AdminStockRepository adminStockRepository;
    @Inject SalesRepository sales;

    public CountSession submit(User user,SubmitCountRequest request){
        require(request!=null,"Contagem obrigatória");
        require(request.countDate!=null,"Data da contagem obrigatória");
        require(request.items!=null&&!request.items.isEmpty(),"Adicione ao menos um produto");
        CountSession session=new CountSession();
        session.id=UUID.randomUUID().toString(); session.countDate=request.countDate;
        session.location=location(request.location); session.createdBy=user.id; session.createdByName=user.name;
        session.countedAt=request.countedAt==null?Instant.now():request.countedAt; session.submittedAt=Instant.now();
        Set<String> duplicateKeys=new HashSet<>();
        for(CountItem input:request.items){
            validate(input); CountItem item=copy(input); item.id=UUID.randomUUID().toString();
            String key=normalize(item.name)+"|"+item.condition+"|"+item.volume.stripTrailingZeros()+"|"+item.volumeUnit;
            require(duplicateKeys.add(key),"Produto duplicado na contagem: "+item.name);
            session.items.add(item);
        }
        repository.save(session); return session;
    }

    public List<CountSession> list(LocalDate from,LocalDate to){
        return repository.list().stream().filter(value->(from==null||!value.countDate.isBefore(from))&&(to==null||!value.countDate.isAfter(to))).toList();
    }
    public void delete(String id){ repository.delete(id); }

    public AdminStockSession submitAdminStock(User user,SubmitCountRequest request){
        require(request!=null,"Contagem administrativa obrigatória");
        require(request.countDate!=null,"Data da contagem obrigatória");
        require(request.items!=null&&!request.items.isEmpty(),"Adicione ao menos um produto");
        String requestedLocation=location(request.location);
        AdminStockSession session=new AdminStockSession();
        session.id=UUID.randomUUID().toString();
        session.countDate=request.countDate;
        session.location=requestedLocation;
        session.createdBy=user.id;
        session.createdByName=user.name;
        session.countedAt=request.countedAt==null?Instant.now():request.countedAt;
        session.submittedAt=Instant.now();
        Set<String> duplicateKeys=new HashSet<>();
        for(CountItem input:request.items){
            validate(input);
            CountItem item=copy(input);
            item.id=UUID.randomUUID().toString();
            String key=normalize(item.name)+"|"+item.condition+"|"+item.volume.stripTrailingZeros()+"|"+item.volumeUnit;
            require(duplicateKeys.add(key),"Produto duplicado na contagem: "+item.name);
            session.items.add(item);
            String productKey=normalize(item.name);
            adminStockRepository.adjustBalance(productKey,item.name,requestedLocation,item.quantity);
        }
        adminStockRepository.saveSession(session);
        return session;
    }

    public List<AdminStockSession> listAdminStock(LocalDate from,LocalDate to){
        return adminStockRepository.listSessions().stream()
            .filter(value->(from==null||!value.countDate.isBefore(from))&&(to==null||!value.countDate.isAfter(to)))
            .toList();
    }

    public List<AdminStockBalance> listAdminStockBalances(){
        return adminStockRepository.listBalances();
    }

    public ApplyDailyAuditResponse applyDailyAudit(User user,DailyAuditRequest request){
        require(request!=null&&request.date!=null,"Data da auditoria obrigatória");
        String requestedLocation=location(request.location);
        ApplyDailyAuditResponse response=new ApplyDailyAuditResponse();
        if(adminStockRepository.appliedAuditAt(request.date,requestedLocation).isPresent()){
            response.audit=audit(request);
            response.balances=adminStockRepository.listBalances();
            response.alreadyApplied=true;
            return response;
        }
        DailyAuditResponse audit=audit(request);
        for(DailyAuditItem item:audit.items){
            if(item.soldQuantity==null||item.soldQuantity.signum()<=0) continue;
            String productKey=normalize(item.product);
            adminStockRepository.adjustBalance(productKey,item.product,requestedLocation,item.soldQuantity.negate());
        }
        adminStockRepository.markAuditApplied(request.date,requestedLocation,Instant.now(),user.id);
        response.audit=audit;
        response.balances=adminStockRepository.listBalances();
        response.alreadyApplied=false;
        return response;
    }

    public DailyAuditResponse audit(DailyAuditRequest request){
        require(request!=null&&request.date!=null,"Data da auditoria obrigatória");
        String requestedLocation=location(request.location);
        Map<String,DailyAuditItem> result=new LinkedHashMap<>();
        repository.list().stream().filter(session->request.date.equals(session.countDate)&&sameLocation(requestedLocation,session.location)).forEach(session->{
            for(CountItem count:session.items){
                if(!matches(request.text,count.name)) continue;
                String key=normalize(count.name);
                DailyAuditItem item=result.computeIfAbsent(key,ignored->auditItem(count,requestedLocation));
                item.openingQuantity=item.openingQuantity.add(count.quantity);
                item.projectedRevenueInCents+=multiply(count.salePriceInCents,count.quantity);
                if(count.costPriceInCents!=null) item.projectedCostInCents+=multiply(count.costPriceInCents,count.quantity);
            }
        });
        for(Sale sale:sales.sales("sales")){
            if(!request.date.equals(sale.saleDate)||!sameLocation(requestedLocation,sale.location)||!matches(request.text,sale.description)) continue;
            String key=bestKey(result.keySet(),normalize(sale.description));
            DailyAuditItem item=result.computeIfAbsent(key,ignored->{ DailyAuditItem value=new DailyAuditItem(); value.product=sale.description; value.category=sale.category; value.location=requestedLocation; return value; });
            item.soldQuantity=item.soldQuantity.add(sale.quantity==null?BigDecimal.ZERO:sale.quantity);
        }
        DailyAuditResponse response=new DailyAuditResponse(); response.date=request.date; response.location=requestedLocation;
        response.items=result.values().stream().peek(this::finish).sorted(Comparator.comparing(value->value.product)).toList();
        for(DailyAuditItem item:response.items){ response.totalOpening=response.totalOpening.add(item.openingQuantity); response.totalSold=response.totalSold.add(item.soldQuantity); response.totalRemaining=response.totalRemaining.add(item.theoreticalRemaining); response.projectedRevenueInCents+=item.projectedRevenueInCents; }
        return response;
    }

    private void finish(DailyAuditItem item){
        item.theoreticalRemaining=item.openingQuantity.subtract(item.soldQuantity);
        if(item.openingQuantity.signum()==0&&item.soldQuantity.signum()>0){ item.status="CRITICO"; item.notes="Venda sem contagem de abertura correspondente"; }
        else if(item.theoreticalRemaining.signum()<0){ item.status="ALERTA"; item.notes="Vendido acima do estoque contado"; }
        else if(item.soldQuantity.signum()==0){ item.status="ATENCAO"; item.notes="Produto contado sem venda registrada"; }
        else { item.status="OK"; item.notes="Saldo teórico calculado com sucesso"; }
    }
    private static DailyAuditItem auditItem(CountItem item,String location){ DailyAuditItem value=new DailyAuditItem(); value.product=item.name; value.category=item.category.name(); value.location=location; return value; }
    private static CountItem copy(CountItem input){ CountItem item=new CountItem(); item.name=input.name.trim(); item.quantity=input.quantity; item.category=input.category; item.volume=input.volume; item.volumeUnit=input.volumeUnit.trim().toUpperCase(Locale.ROOT); item.salePriceInCents=input.salePriceInCents; item.costPriceInCents=input.costPriceInCents; item.condition=input.condition; return item; }
    private static void validate(CountItem item){ require(item!=null,"Item inválido"); require(item.name!=null&&!item.name.isBlank(),"Nome do produto obrigatório"); require(item.quantity!=null&&item.quantity.signum()>=0,"Quantidade inválida para "+item.name); require(item.category!=null,"Categoria obrigatória para "+item.name); require(item.volume!=null&&item.volume.signum()>0,"Volume inválido para "+item.name); require(item.volumeUnit!=null&&(item.volumeUnit.equalsIgnoreCase("ML")||item.volumeUnit.equalsIgnoreCase("G")),"Unidade deve ser ML ou G"); require(item.salePriceInCents>=0,"Valor de venda inválido"); require(item.costPriceInCents==null||item.costPriceInCents>=0,"Preço de custo inválido"); require(item.condition!=null,"Conservação obrigatória para "+item.name); }
    private static long multiply(long cents,BigDecimal quantity){ return quantity.multiply(BigDecimal.valueOf(cents)).longValue(); }
    private static String bestKey(Set<String> keys,String sale){ return keys.stream().filter(key->sale.contains(key)||key.contains(sale)).max(Comparator.comparingInt(String::length)).orElse(sale); }
    private static boolean matches(String text,String value){ return text==null||text.isBlank()||normalize(value).contains(normalize(text)); }
    private static boolean sameLocation(String a,String b){ return normalizeLocation(a).equals(normalizeLocation(b)); }
    private static String normalizeLocation(String value){ String current=normalize(value); return current.equals("beco")||current.equals("beco da praia")?"beco da praia":current; }
    private static String location(String value){ return value==null||value.isBlank()||normalizeLocation(value).equals("beco da praia")?"Beco da Praia":value.trim(); }
    private static String normalize(String value){ return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim(); }
    private static void require(boolean valid,String message){ if(!valid) throw new IllegalArgumentException(message); }
}
