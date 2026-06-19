package com.checklistboteco.backend.sales.application;

import static com.checklistboteco.backend.sales.domain.SalesModels.*;

import com.checklistboteco.backend.purchases.domain.PurchaseModels.Purchase;
import com.checklistboteco.backend.purchases.persistence.PurchaseRepository;
import com.checklistboteco.backend.sales.domain.SalesModels.Sale;
import com.checklistboteco.backend.sales.persistence.SalesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;
import java.util.*;

@ApplicationScoped
public class SalesAuditService {
    @Inject PurchaseRepository purchases;
    @Inject SalesRepository sales;

    public SalesAuditResponse audit(SalesAuditRequest request){
        SalesAuditRequest query=request==null?new SalesAuditRequest():request;
        validate(query);
        Map<String,SalesAuditRow> rows=new LinkedHashMap<>();
        for(Purchase purchase:purchases.purchases(purchaseDataset(query.purchaseDatasetId))){
            if(!matchesPurchase(query,purchase)) continue;
            String key=key(purchase.description,purchase.location);
            SalesAuditRow row=rows.computeIfAbsent(key,current->row(purchase.description,purchase.category,purchase.location,key));
            row.stockedRecords++;
            row.stockedQuantity=row.stockedQuantity.add(purchase.quantity==null?BigDecimal.ZERO:purchase.quantity);
        }
        for(Sale saleValue:sales.sales(salesDataset(query.salesDatasetId))){
            if(!matchesSale(query,saleValue)) continue;
            String key=key(saleValue.description,saleValue.location);
            SalesAuditRow row=rows.computeIfAbsent(key,current->row(saleValue.description,saleValue.category,saleValue.location,key));
            row.soldRecords++;
            row.soldQuantity=row.soldQuantity.add(saleValue.quantity==null?BigDecimal.ZERO:saleValue.quantity);
        }
        rows.values().forEach(this::finalizeRow);

        SalesAuditResponse response=new SalesAuditResponse(); response.purchaseDatasetId=purchaseDataset(query.purchaseDatasetId); response.salesDatasetId=salesDataset(query.salesDatasetId);
        response.from=query.from; response.to=query.to; response.filtersApplied=filters(query);
        response.items=rows.values().stream().filter(this::relevant).sorted(Comparator
            .comparing((SalesAuditRow value)->severity(value.status)).reversed()
            .thenComparing(value->value.differenceQuantity.abs(),Comparator.reverseOrder())
            .thenComparing(value->Objects.toString(value.description,""))).limit(Math.max(1,Math.min(500,query.limit))).toList();
        response.totalItems=response.items.size();
        return response;
    }

    private boolean matchesPurchase(SalesAuditRequest query,Purchase purchase){
        if(purchase==null) return false;
        if(query.from!=null&&(purchase.purchaseDate==null||purchase.purchaseDate.isBefore(query.from)||purchase.purchaseDate.isAfter(query.to))) return false;
        if(query.locations!=null&&!query.locations.isEmpty()&&query.locations.stream().noneMatch(value->equalsIgnoreCase(value,purchase.location))) return false;
        return matchesText(query.text,purchase.description,purchase.category,purchase.location);
    }
    private boolean matchesSale(SalesAuditRequest query,Sale sale){
        if(sale==null) return false;
        if(query.from!=null&&(sale.saleDate==null||sale.saleDate.isBefore(query.from)||sale.saleDate.isAfter(query.to))) return false;
        if(query.locations!=null&&!query.locations.isEmpty()&&query.locations.stream().noneMatch(value->equalsIgnoreCase(value,sale.location))) return false;
        return matchesText(query.text,sale.description,sale.category,sale.location);
    }
    private static boolean matchesText(String text,String... values){
        if(text==null||text.isBlank()) return true;
        String search=normalize(text);
        for(String value:values) if(normalize(value).contains(search)) return true;
        return false;
    }
    private static String purchaseDataset(String value){ return value==null||value.isBlank()?"purchases":value.trim(); }
    private static String salesDataset(String value){ return value==null||value.isBlank()?"sales":value.trim(); }
    private static SalesAuditRow row(String description,String category,String location,String key){
        SalesAuditRow row=new SalesAuditRow(); row.description=description; row.category=category; row.location=location; row.productKey=key; return row;
    }
    private void finalizeRow(SalesAuditRow row){
        row.differenceQuantity=row.stockedQuantity.subtract(row.soldQuantity);
        if(row.stockedQuantity.compareTo(BigDecimal.ZERO)<=0&&row.soldQuantity.compareTo(BigDecimal.ZERO)>0){
            row.status="CRITICO"; row.notes="Venda registrada sem abastecimento correspondente";
        } else if(row.differenceQuantity.compareTo(BigDecimal.ZERO)<0){
            row.status="ALERTA"; row.notes="Vendido acima da quantidade abastecida";
        } else if(row.stockedQuantity.compareTo(BigDecimal.ZERO)>0&&row.soldQuantity.compareTo(BigDecimal.ZERO)==0){
            row.status="ATENCAO"; row.notes="Abastecido sem saída registrada no período";
        } else {
            row.status="OK"; row.notes="Saídas compatíveis com o abastecimento importado";
        }
    }
    private boolean relevant(SalesAuditRow row){ return row.stockedRecords>0||row.soldRecords>0; }
    private static int severity(String status){ return switch(Objects.toString(status,"OK")){ case "CRITICO"->4; case "ALERTA"->3; case "ATENCAO"->2; default->1; }; }
    private static String key(String description,String location){ return normalize(description)+"|"+normalize(location); }
    private static String normalize(String value){ return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim(); }
    private static boolean equalsIgnoreCase(String a,String b){ return a!=null&&b!=null&&a.equalsIgnoreCase(b); }
    private static List<String> filters(SalesAuditRequest query){ List<String> values=new ArrayList<>(); if(query.from!=null){ values.add("from="+query.from); values.add("to="+query.to); } if(query.locations!=null&&!query.locations.isEmpty()) values.add("locations="+query.locations); if(query.text!=null&&!query.text.isBlank()) values.add("text="+query.text); return values; }
    private static void validate(SalesAuditRequest query){
        if((query.from==null)!=(query.to==null)) throw new IllegalArgumentException("Informe as duas datas para auditar por período");
        if(query.from!=null&&query.to.isBefore(query.from)) throw new IllegalArgumentException("Período final anterior ao inicial");
        if(query.from!=null&&ChronoUnit.DAYS.between(query.from,query.to)>3660) throw new IllegalArgumentException("Período máximo de 10 anos");
        query.limit=Math.max(1,Math.min(500,query.limit));
    }
}
