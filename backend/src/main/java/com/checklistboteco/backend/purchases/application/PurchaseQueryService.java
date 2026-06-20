package com.checklistboteco.backend.purchases.application;

import static com.checklistboteco.backend.purchases.domain.PurchaseModels.*;

import com.checklistboteco.backend.purchases.persistence.PurchaseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@ApplicationScoped
public class PurchaseQueryService {
    private static final Set<String> NORMALIZED=Set.of("purchaseDate","description","category","location","supplier","documentNumber","quantity","unit","unitPriceInCents","totalInCents");
    @Inject PurchaseRepository repository;

    public ImportSchema schema(String datasetId){
        String dataset=dataset(datasetId); List<Purchase> values=repository.purchases(dataset); ImportSchema schema=new ImportSchema(); schema.datasetId=dataset; schema.purchaseCount=values.size();
        schema.fields.add(new SchemaField("purchaseDate","Data","DATE",true)); schema.fields.add(new SchemaField("description","Mercadoria","TEXT",true));
        schema.fields.add(new SchemaField("category","Categoria","TEXT",true)); schema.fields.add(new SchemaField("location","Local","TEXT",true)); schema.fields.add(new SchemaField("supplier","Fornecedor","TEXT",true));
        schema.fields.add(new SchemaField("documentNumber","Documento","TEXT",true)); schema.fields.add(new SchemaField("quantity","Quantidade","NUMBER",true));
        schema.fields.add(new SchemaField("unit","Unidade","TEXT",true)); schema.fields.add(new SchemaField("unitPriceInCents","Valor unitário","MONEY",true));
        schema.fields.add(new SchemaField("totalInCents","Total","MONEY",true));
        Map<String,String> dynamic=new TreeMap<>();
        for(Purchase p:values){ if(p.purchaseDate!=null&&(schema.coverageFrom==null||p.purchaseDate.isBefore(schema.coverageFrom))) schema.coverageFrom=p.purchaseDate; if(p.purchaseDate!=null&&(schema.coverageTo==null||p.purchaseDate.isAfter(schema.coverageTo))) schema.coverageTo=p.purchaseDate;
            p.attributes.forEach((key,value)->dynamic.merge(key,type(value),(a,b)->a.equals(b)?a:"TEXT")); }
        dynamic.forEach((key,type)->schema.fields.add(new SchemaField(key,label(key),type,false))); return schema;
    }

    public PurchasePage query(String datasetId,PurchaseQuery request){
        PurchaseQuery q=request==null?new PurchaseQuery():request; validate(q,datasetId); List<Purchase> filtered=new ArrayList<>(filtered(datasetId,q));
        Comparator<Purchase> comparator=comparator(q); filtered.sort(comparator); PurchasePage result=new PurchasePage(); result.page=Math.max(0,q.page); result.pageSize=Math.max(1,Math.min(200,q.pageSize));
        result.totalItems=filtered.size(); result.totalInCents=filtered.stream().mapToLong(p->p.totalInCents).sum(); result.totalPages=(int)Math.ceil(result.totalItems/(double)result.pageSize);
        int from=Math.min(filtered.size(),result.page*result.pageSize),to=Math.min(filtered.size(),from+result.pageSize); result.items=new ArrayList<>(filtered.subList(from,to)); result.filtersApplied=filters(q); return result;
    }

    public AggregateResponse aggregate(String datasetId,AggregateRequest request){
        if(request==null) throw new IllegalArgumentException("Consulta obrigatória"); validate(request,datasetId); ImportSchema schema=schema(datasetId);
        boolean valid=NORMALIZED.contains(request.groupBy)||schema.fields.stream().anyMatch(f->!f.normalized&&Objects.equals(f.key,request.groupBy));
        if(!valid) throw new IllegalArgumentException("Campo de agrupamento inválido: "+request.groupBy);
        List<Purchase> values=filtered(datasetId,request); Map<String,AggregateBucket> groups=new LinkedHashMap<>();
        for(Purchase p:values){ String key=Objects.toString(field(p,request.groupBy),"Sem valor"); AggregateBucket bucket=groups.computeIfAbsent(key,k->{ var b=new AggregateBucket(); b.key=k; return b; }); bucket.count++; bucket.totalInCents+=p.totalInCents; bucket.quantity=bucket.quantity.add(p.quantity==null?BigDecimal.ZERO:p.quantity); }
        AggregateResponse result=new AggregateResponse(); result.groupBy=request.groupBy; result.totalItems=values.size(); result.totalInCents=values.stream().mapToLong(p->p.totalInCents).sum();
        result.groups=groups.values().stream().sorted(Comparator.comparingLong((AggregateBucket b)->b.totalInCents).reversed()).limit(100).toList(); return result;
    }

    private List<Purchase> filtered(String datasetId,PurchaseQuery q){
        String search=q.text==null?null:q.text.trim().toLowerCase(Locale.ROOT);
        return repository.purchases(dataset(datasetId)).stream().filter(p->q.from==null||q.to==null||(p.purchaseDate!=null&&!p.purchaseDate.isBefore(q.from)&&!p.purchaseDate.isAfter(q.to)))
            .filter(p->q.categories==null||q.categories.isEmpty()||q.categories.stream().anyMatch(v->equalsIgnoreCase(v,p.category)))
            .filter(p->q.locations==null||q.locations.isEmpty()||q.locations.stream().anyMatch(v->equalsIgnoreCase(v,p.location)))
            .filter(p->q.suppliers==null||q.suppliers.isEmpty()||q.suppliers.stream().anyMatch(v->equalsIgnoreCase(v,p.supplier)))
            .filter(p->q.minTotalInCents==null||p.totalInCents>=q.minTotalInCents).filter(p->q.maxTotalInCents==null||p.totalInCents<=q.maxTotalInCents)
            .filter(p->search==null||search.isBlank()||contains(p.description,search)||contains(p.category,search)||contains(p.location,search)||contains(p.supplier,search)||p.attributes.values().stream().anyMatch(v->contains(Objects.toString(v,""),search)))
            .filter(p->matchesAttributes(p,q.attributes)).toList();
    }
    private void validate(PurchaseQuery q,String datasetId){
        if((q.from==null)!=(q.to==null)) throw new IllegalArgumentException("Informe as duas datas para filtrar por período");
        if(q.from!=null&&q.to.isBefore(q.from)) throw new IllegalArgumentException("Período final anterior ao inicial"); if(q.from!=null&&ChronoUnit.DAYS.between(q.from,q.to)>3660) throw new IllegalArgumentException("Período máximo de 10 anos");
        Set<String> dynamic=new HashSet<>(); schema(datasetId).fields.stream().filter(f->!f.normalized).forEach(f->dynamic.add(f.key));
        if(q.attributes!=null) for(var entry:q.attributes.entrySet()){ if(!dynamic.contains(entry.getKey())) throw new IllegalArgumentException("Atributo desconhecido: "+entry.getKey()); validateOperator(entry.getValue()); }
        q.pageSize=Math.max(1,Math.min(200,q.pageSize)); q.page=Math.max(0,q.page);
    }
    private static void validateOperator(AttributeCondition value){
        Set<String> allowed=Set.of("EQUALS","CONTAINS","STARTS_WITH","IN","GT","GTE","LT","LTE","BETWEEN");
        if(value==null||!allowed.contains(Objects.toString(value.operator,"").toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Operador de atributo inválido");
    }
    private static boolean matchesAttributes(Purchase p,Map<String,AttributeCondition> filters){ if(filters==null||filters.isEmpty()) return true; for(var entry:filters.entrySet()) if(!match(p.attributes.get(entry.getKey()),entry.getValue())) return false; return true; }
    private static boolean match(Object actual,AttributeCondition condition){
        if(actual==null) return false; String operator=condition.operator.toUpperCase(Locale.ROOT); String left=Objects.toString(actual,""); String right=Objects.toString(condition.value,"");
        return switch(operator){
            case "EQUALS" -> left.equalsIgnoreCase(right); case "CONTAINS" -> left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT)); case "STARTS_WITH" -> left.toLowerCase(Locale.ROOT).startsWith(right.toLowerCase(Locale.ROOT));
            case "IN" -> condition.value instanceof Collection<?> values&&values.stream().anyMatch(v->left.equalsIgnoreCase(Objects.toString(v,"")));
            case "GT" -> decimal(left).compareTo(decimal(right))>0; case "GTE" -> decimal(left).compareTo(decimal(right))>=0; case "LT" -> decimal(left).compareTo(decimal(right))<0; case "LTE" -> decimal(left).compareTo(decimal(right))<=0;
            case "BETWEEN" -> decimal(left).compareTo(decimal(right))>=0&&decimal(left).compareTo(decimal(Objects.toString(condition.to,"")))<=0; default -> false;
        };
    }
    private static Comparator<Purchase> comparator(PurchaseQuery q){
        SortField sort=q.sort==null||q.sort.isEmpty()?new SortField():q.sort.get(0); Function<Purchase,Comparable> getter=p->{ Object value=field(p,sort.field); return value instanceof Comparable<?> c?(Comparable)c:Objects.toString(value,""); };
        Comparator<Purchase> comparator=Comparator.comparing(getter,Comparator.nullsLast(Comparator.naturalOrder())); return "ASC".equalsIgnoreCase(sort.direction)?comparator:comparator.reversed();
    }
    private static Object field(Purchase p,String name){ return switch(Objects.toString(name,"")){ case "purchaseDate"->p.purchaseDate; case "description"->p.description; case "category"->p.category; case "location"->p.location; case "supplier"->p.supplier; case "documentNumber"->p.documentNumber; case "quantity"->p.quantity; case "unit"->p.unit; case "unitPriceInCents"->p.unitPriceInCents; case "totalInCents"->p.totalInCents; case "importedAt"->p.importedAt; default->p.attributes.get(name); }; }
    private static List<String> filters(PurchaseQuery q){ List<String> values=new ArrayList<>(); if(q.from!=null){ values.add("from="+q.from); values.add("to="+q.to); } if(q.categories!=null&&!q.categories.isEmpty()) values.add("categories="+q.categories); if(q.locations!=null&&!q.locations.isEmpty()) values.add("locations="+q.locations); if(q.suppliers!=null&&!q.suppliers.isEmpty()) values.add("suppliers="+q.suppliers); if(q.minTotalInCents!=null) values.add("minTotalInCents="+q.minTotalInCents); if(q.maxTotalInCents!=null) values.add("maxTotalInCents="+q.maxTotalInCents); if(q.text!=null&&!q.text.isBlank()) values.add("text="+q.text); if(q.attributes!=null&&!q.attributes.isEmpty()) values.add("attributes="+q.attributes.keySet()); return values; }
    private static String dataset(String value){ return value==null||value.isBlank()?"purchases":value.trim(); }
    private static boolean equalsIgnoreCase(String a,String b){ return a!=null&&b!=null&&a.equalsIgnoreCase(b); }
    private static boolean contains(String value,String search){ return value!=null&&value.toLowerCase(Locale.ROOT).contains(search); }
    private static BigDecimal decimal(String value){ try { return new BigDecimal(value.replace(',','.')); } catch(Exception e){ return BigDecimal.ZERO; } }
    private static String type(Object value){ return value instanceof Number?"NUMBER":value instanceof LocalDate?"DATE":"TEXT"; }
    private static String label(String key){ if(key==null||key.isBlank()) return "Atributo"; String words=key.replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' '); return words.substring(0,1).toUpperCase(Locale.ROOT)+words.substring(1); }
}
