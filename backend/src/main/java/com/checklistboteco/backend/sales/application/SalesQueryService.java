package com.checklistboteco.backend.sales.application;

import static com.checklistboteco.backend.sales.domain.SalesModels.*;

import com.checklistboteco.backend.sales.persistence.SalesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

@ApplicationScoped
public class SalesQueryService {
    private static final Set<String> NORMALIZED=Set.of("saleDate","description","category","location","seller","documentNumber","quantity","unit","unitPriceInCents","totalInCents","serviceChargeInCents");
    @Inject SalesRepository repository;

    public ImportSchema schema(String datasetId){
        String dataset=dataset(datasetId); List<Sale> values=repository.sales(dataset); ImportSchema schema=new ImportSchema(); schema.datasetId=dataset; schema.saleCount=values.size();
        schema.fields.add(new SchemaField("saleDate","Data","DATE",true)); schema.fields.add(new SchemaField("description","Produto","TEXT",true));
        schema.fields.add(new SchemaField("category","Categoria","TEXT",true)); schema.fields.add(new SchemaField("location","Local","TEXT",true)); schema.fields.add(new SchemaField("seller","Usuário/Vendedor","TEXT",true)); schema.fields.add(new SchemaField("documentNumber","Documento","TEXT",true));
        schema.fields.add(new SchemaField("quantity","Quantidade","NUMBER",true)); schema.fields.add(new SchemaField("unit","Unidade","TEXT",true)); schema.fields.add(new SchemaField("unitPriceInCents","Valor unitário","MONEY",true));
        schema.fields.add(new SchemaField("totalInCents","Total","MONEY",true)); schema.fields.add(new SchemaField("serviceChargeInCents","10% serviço","MONEY",true));
        Map<String,String> dynamic=new TreeMap<>();
        for(Sale sale:values){ if(sale.saleDate!=null&&(schema.coverageFrom==null||sale.saleDate.isBefore(schema.coverageFrom))) schema.coverageFrom=sale.saleDate; if(sale.saleDate!=null&&(schema.coverageTo==null||sale.saleDate.isAfter(schema.coverageTo))) schema.coverageTo=sale.saleDate;
            sale.attributes.forEach((key,value)->dynamic.merge(key,type(value),(a,b)->a.equals(b)?a:"TEXT")); }
        dynamic.forEach((key,type)->schema.fields.add(new SchemaField(key,label(key),type,false))); return schema;
    }

    public SalePage query(String datasetId,SaleQuery request){
        SaleQuery query=request==null?new SaleQuery():request; validate(query,datasetId); List<Sale> filtered=new ArrayList<>(filtered(datasetId,query));
        Comparator<Sale> comparator=comparator(query); filtered.sort(comparator); SalePage result=new SalePage(); result.page=Math.max(0,query.page); result.pageSize=Math.max(1,Math.min(200,query.pageSize));
        result.totalItems=filtered.size(); result.totalInCents=filtered.stream().mapToLong(sale->sale.totalInCents).sum(); result.serviceChargeInCents=filtered.stream().mapToLong(sale->sale.serviceChargeInCents).sum(); result.totalQuantity=filtered.stream().map(sale->sale.quantity==null?BigDecimal.ZERO:sale.quantity).reduce(BigDecimal.ZERO,BigDecimal::add); result.totalPages=(int)Math.ceil(result.totalItems/(double)result.pageSize);
        int from=Math.min(filtered.size(),result.page*result.pageSize),to=Math.min(filtered.size(),from+result.pageSize); result.items=new ArrayList<>(filtered.subList(from,to)); result.filtersApplied=filters(query); return result;
    }

    public AggregateResponse aggregate(String datasetId,AggregateRequest request){
        if(request==null) throw new IllegalArgumentException("Consulta obrigatória"); validate(request,datasetId); ImportSchema schema=schema(datasetId);
        boolean valid=NORMALIZED.contains(request.groupBy)||schema.fields.stream().anyMatch(field->!field.normalized&&Objects.equals(field.key,request.groupBy));
        if(!valid) throw new IllegalArgumentException("Campo de agrupamento inválido: "+request.groupBy);
        List<Sale> values=filtered(datasetId,request); Map<String,AggregateBucket> groups=new LinkedHashMap<>();
        for(Sale sale:values){ String key=groupKey(field(sale,request.groupBy)); AggregateBucket bucket=groups.computeIfAbsent(key,current->{ var created=new AggregateBucket(); created.key=current; return created; }); bucket.count++; bucket.totalInCents+=sale.totalInCents; bucket.serviceChargeInCents+=sale.serviceChargeInCents; bucket.quantity=bucket.quantity.add(sale.quantity==null?BigDecimal.ZERO:sale.quantity); }
        AggregateResponse result=new AggregateResponse(); result.groupBy=request.groupBy; result.totalItems=values.size(); result.totalInCents=values.stream().mapToLong(sale->sale.totalInCents).sum(); result.serviceChargeInCents=values.stream().mapToLong(sale->sale.serviceChargeInCents).sum(); result.totalQuantity=values.stream().map(sale->sale.quantity==null?BigDecimal.ZERO:sale.quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        result.groups=groups.values().stream().sorted(Comparator.comparing((AggregateBucket bucket)->bucket.quantity,Comparator.reverseOrder()).thenComparingLong(bucket->-bucket.totalInCents)).limit(100).toList(); return result;
    }

    public SalesHeatmapResponse heatmap(String datasetId, int year){
        if(year<2000||year>2100) throw new IllegalArgumentException("Ano inválido");
        LocalDate from=LocalDate.of(year,1,1);
        LocalDate to=LocalDate.of(year,12,31);
        LocalDate today=LocalDate.now();
        if(year==today.getYear()&&to.isAfter(today)) to=today;
        SaleQuery query=new SaleQuery();
        query.from=from;
        query.to=to;
        Map<LocalDate,BigDecimal> qtyByDay=new TreeMap<>();
        Map<LocalDate,Long> centsByDay=new TreeMap<>();
        for(Sale sale:filtered(datasetId,query)){
            if(sale.saleDate==null) continue;
            BigDecimal qty=sale.quantity==null?BigDecimal.ZERO:sale.quantity;
            qtyByDay.merge(sale.saleDate,qty,BigDecimal::add);
            centsByDay.merge(sale.saleDate,sale.totalInCents,Long::sum);
        }
        SalesHeatmapResponse result=new SalesHeatmapResponse();
        result.year=year;
        result.datasetId=dataset(datasetId);
        for(LocalDate date:qtyByDay.keySet()){
            BigDecimal qty=qtyByDay.getOrDefault(date,BigDecimal.ZERO);
            long cents=centsByDay.getOrDefault(date,0L);
            if((qty!=null&&qty.compareTo(BigDecimal.ZERO)>0)||cents>0){
                result.days.add(new SalesHeatmapDay(date.toString(),qty,cents));
            }
        }
        return result;
    }

    public ProductSearchResponse byProduct(String datasetId,ProductSearchRequest request){
        ProductSearchRequest query=request==null?new ProductSearchRequest():request;
        if(query.product==null||query.product.isBlank()) throw new IllegalArgumentException("Informe o nome ou trecho do produto");
        validate(query,datasetId);
        List<Sale> values=filtered(datasetId,query).stream().filter(sale->productMatches(sale.description,query.product)).toList();
        Map<String,ProductMatch> groups=new LinkedHashMap<>();
        for(Sale sale:values){
            String key=Objects.toString(sale.description,"Sem descrição")+"|"+Objects.toString(sale.location,"Sem local");
            ProductMatch match=groups.computeIfAbsent(key,current->{
                ProductMatch created=new ProductMatch();
                created.description=sale.description;
                created.category=sale.category;
                created.location=sale.location;
                return created;
            });
            match.salesCount++;
            match.totalInCents+=sale.totalInCents;
            match.quantity=match.quantity.add(sale.quantity==null?BigDecimal.ZERO:sale.quantity);
        }
        ProductSearchResponse response=new ProductSearchResponse();
        response.datasetId=dataset(datasetId);
        response.product=query.product;
        response.from=query.from;
        response.to=query.to;
        response.locations=query.locations==null?List.of():new ArrayList<>(query.locations);
        response.filtersApplied=filters(query);
        response.totalItems=values.size();
        response.totalInCents=values.stream().mapToLong(sale->sale.totalInCents).sum();
        response.totalQuantity=values.stream().map(sale->sale.quantity==null?BigDecimal.ZERO:sale.quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        response.items=groups.values().stream().sorted(Comparator.comparing((ProductMatch item)->item.quantity,Comparator.reverseOrder()).thenComparingLong(item->-item.totalInCents)).limit(Math.max(1,Math.min(100,query.limit))).toList();
        return response;
    }

    public SellerSalesResponse bySeller(String datasetId,SellerSearchRequest request){
        SellerSearchRequest query=request==null?new SellerSearchRequest():request;
        if(query.seller!=null&&!query.seller.isBlank()) query.sellers=List.of(query.seller.trim());
        validate(query,datasetId);
        List<Sale> values=filtered(datasetId,query);
        Map<String,SellerSalesMatch> groups=new LinkedHashMap<>();
        for(Sale sale:values){
            String seller=sellerLabel(sale.seller);
            String key=seller+"|"+Objects.toString(sale.location,"Sem local");
            SellerSalesMatch match=groups.computeIfAbsent(key,current->{
                SellerSalesMatch created=new SellerSalesMatch();
                created.seller=seller;
                created.location=sale.location;
                return created;
            });
            match.salesCount++;
            match.totalInCents+=sale.totalInCents;
            match.serviceChargeInCents+=sale.serviceChargeInCents;
            match.quantity=match.quantity.add(sale.quantity==null?BigDecimal.ZERO:sale.quantity);
        }
        SellerSalesResponse response=new SellerSalesResponse();
        response.datasetId=dataset(datasetId);
        response.seller=query.seller;
        response.from=query.from;
        response.to=query.to;
        response.locations=query.locations==null?List.of():new ArrayList<>(query.locations);
        response.filtersApplied=filters(query);
        response.totalItems=values.size();
        response.totalInCents=values.stream().mapToLong(sale->sale.totalInCents).sum();
        response.serviceChargeInCents=values.stream().mapToLong(sale->sale.serviceChargeInCents).sum();
        response.totalQuantity=values.stream().map(sale->sale.quantity==null?BigDecimal.ZERO:sale.quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        response.items=groups.values().stream().sorted(Comparator.comparingLong((SellerSalesMatch item)->item.totalInCents).reversed()).limit(Math.max(1,Math.min(100,query.limit))).toList();
        return response;
    }

    public List<Sale> filteredSales(String datasetId,SaleQuery query){ return filtered(datasetId,query); }

    private List<Sale> filtered(String datasetId,SaleQuery query){
        String search=query.text==null?null:query.text.trim().toLowerCase(Locale.ROOT);
        return repository.sales(dataset(datasetId)).stream().filter(sale->query.from==null||query.to==null||(sale.saleDate!=null&&!sale.saleDate.isBefore(query.from)&&!sale.saleDate.isAfter(query.to)))
            .filter(sale->query.categories==null||query.categories.isEmpty()||query.categories.stream().anyMatch(value->equalsIgnoreCase(value,sale.category)))
            .filter(sale->query.locations==null||query.locations.isEmpty()||query.locations.stream().anyMatch(value->equalsIgnoreCase(value,sale.location)))
            .filter(sale->query.sellers==null||query.sellers.isEmpty()||query.sellers.stream().anyMatch(value->contains(sale.seller,value)))
            .filter(sale->query.minTotalInCents==null||sale.totalInCents>=query.minTotalInCents).filter(sale->query.maxTotalInCents==null||sale.totalInCents<=query.maxTotalInCents)
            .filter(sale->search==null||search.isBlank()||contains(sale.description,search)||contains(sale.category,search)||contains(sale.location,search)||contains(sale.seller,search)||sale.attributes.values().stream().anyMatch(value->contains(Objects.toString(value,""),search)))
            .filter(sale->matchesAttributes(sale,query.attributes)).toList();
    }
    private void validate(SaleQuery query,String datasetId){
        if((query.from==null)!=(query.to==null)) throw new IllegalArgumentException("Informe as duas datas para filtrar por período");
        if(query.from!=null&&query.to.isBefore(query.from)) throw new IllegalArgumentException("Período final anterior ao inicial"); if(query.from!=null&&ChronoUnit.DAYS.between(query.from,query.to)>3660) throw new IllegalArgumentException("Período máximo de 10 anos");
        Set<String> dynamic=new HashSet<>(); schema(datasetId).fields.stream().filter(field->!field.normalized).forEach(field->dynamic.add(field.key));
        if(query.attributes!=null) for(var entry:query.attributes.entrySet()){ if(!dynamic.contains(entry.getKey())) throw new IllegalArgumentException("Atributo desconhecido: "+entry.getKey()); validateOperator(entry.getValue()); }
        query.pageSize=Math.max(1,Math.min(200,query.pageSize)); query.page=Math.max(0,query.page);
    }
    private static void validateOperator(AttributeCondition value){
        Set<String> allowed=Set.of("EQUALS","CONTAINS","STARTS_WITH","IN","GT","GTE","LT","LTE","BETWEEN");
        if(value==null||!allowed.contains(Objects.toString(value.operator,"").toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Operador de atributo inválido");
    }
    private static boolean matchesAttributes(Sale sale,Map<String,AttributeCondition> filters){ if(filters==null||filters.isEmpty()) return true; for(var entry:filters.entrySet()) if(!match(sale.attributes.get(entry.getKey()),entry.getValue())) return false; return true; }
    private static boolean match(Object actual,AttributeCondition condition){
        if(actual==null) return false; String operator=condition.operator.toUpperCase(Locale.ROOT); String left=Objects.toString(actual,""); String right=Objects.toString(condition.value,"");
        return switch(operator){
            case "EQUALS" -> left.equalsIgnoreCase(right); case "CONTAINS" -> left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT)); case "STARTS_WITH" -> left.toLowerCase(Locale.ROOT).startsWith(right.toLowerCase(Locale.ROOT));
            case "IN" -> condition.value instanceof Collection<?> values&&values.stream().anyMatch(value->left.equalsIgnoreCase(Objects.toString(value,"")));
            case "GT" -> decimal(left).compareTo(decimal(right))>0; case "GTE" -> decimal(left).compareTo(decimal(right))>=0; case "LT" -> decimal(left).compareTo(decimal(right))<0; case "LTE" -> decimal(left).compareTo(decimal(right))<=0;
            case "BETWEEN" -> decimal(left).compareTo(decimal(right))>=0&&decimal(left).compareTo(decimal(Objects.toString(condition.to,"")))<=0; default -> false;
        };
    }
    private static Comparator<Sale> comparator(SaleQuery query){
        SortField sort=query.sort==null||query.sort.isEmpty()?new SortField():query.sort.get(0); Function<Sale,Comparable> getter=sale->{ Object value=field(sale,sort.field); return value instanceof Comparable<?> comparable?(Comparable)comparable:Objects.toString(value,""); };
        Comparator<Sale> comparator=Comparator.comparing(getter,Comparator.nullsLast(Comparator.naturalOrder())); return "ASC".equalsIgnoreCase(sort.direction)?comparator:comparator.reversed();
    }
    private static Object field(Sale sale,String name){ return switch(Objects.toString(name,"")){ case "saleDate"->sale.saleDate; case "description"->sale.description; case "category"->sale.category; case "location"->sale.location; case "seller"->sale.seller; case "documentNumber"->sale.documentNumber; case "quantity"->sale.quantity; case "unit"->sale.unit; case "unitPriceInCents"->sale.unitPriceInCents; case "totalInCents"->sale.totalInCents; case "serviceChargeInCents"->sale.serviceChargeInCents; case "importedAt"->sale.importedAt; default->sale.attributes.get(name); }; }
    private static List<String> filters(SaleQuery query){ List<String> values=new ArrayList<>(); if(query.from!=null){ values.add("from="+query.from); values.add("to="+query.to); } if(query.categories!=null&&!query.categories.isEmpty()) values.add("categories="+query.categories); if(query.locations!=null&&!query.locations.isEmpty()) values.add("locations="+query.locations); if(query.sellers!=null&&!query.sellers.isEmpty()) values.add("sellers="+query.sellers); if(query.minTotalInCents!=null) values.add("minTotalInCents="+query.minTotalInCents); if(query.maxTotalInCents!=null) values.add("maxTotalInCents="+query.maxTotalInCents); if(query.text!=null&&!query.text.isBlank()) values.add("text="+query.text); if(query.attributes!=null&&!query.attributes.isEmpty()) values.add("attributes="+query.attributes.keySet()); return values; }
    private static String dataset(String value){ return value==null||value.isBlank()?"sales":value.trim(); }
    private static boolean equalsIgnoreCase(String a,String b){ return a!=null&&b!=null&&a.equalsIgnoreCase(b); }
    /** Accent-insensitive substring match (web text filter and sellers). */
    private static boolean contains(String value,String search){
        if(value==null||search==null||search.isBlank()) return false;
        return normalize(value).contains(normalize(search));
    }
    /**
     * Product match for AI/MCP: ignores accents/punctuation, tolerates simple plurals
     * (caldos→caldo) and requires all significant query tokens to appear in the description.
     */
    static boolean productMatches(String description,String product){
        if(description==null||product==null||product.isBlank()) return false;
        String hay=normalize(description);
        String needle=normalize(product);
        if(hay.isBlank()||needle.isBlank()) return false;
        if(hay.contains(needle)) return true;
        List<String> queryTokens=significantTokens(needle);
        if(queryTokens.isEmpty()) return false;
        List<String> hayTokens=significantTokens(hay);
        return queryTokens.stream().allMatch(qt->hayTokens.stream().anyMatch(ht->tokensCompatible(qt,ht)));
    }
    private static boolean tokensCompatible(String query,String hay){
        if(query.equals(hay)) return true;
        String q=stripSimplePlural(query);
        String h=stripSimplePlural(hay);
        if(q.equals(h)) return true;
        if(q.length()>=4&&h.length()>=3&&(h.startsWith(q)||q.startsWith(h))) return true;
        return hay.contains(query)||query.contains(hay);
    }
    private static String stripSimplePlural(String token){
        if(token.length()>3&&token.endsWith("s")&&!token.endsWith("ss")) return token.substring(0,token.length()-1);
        return token;
    }
    private static final Set<String> PRODUCT_STOPWORDS=Set.of(
        "de","da","do","das","dos","a","o","e","em","no","na","nos","nas","um","uma","uns","umas","com","por","para","ao","aos","as","os"
    );
    private static List<String> significantTokens(String normalized){
        List<String> out=new ArrayList<>();
        for(String token:normalized.split("\\s+")){
            if(token.length()<2||PRODUCT_STOPWORDS.contains(token)) continue;
            out.add(token);
        }
        return out;
    }
    static String normalize(String value){
        return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+"," ")
            .trim()
            .replaceAll("\\s+"," ");
    }
    private static String sellerLabel(String value){ return value==null||value.isBlank()?"Sem usuário":value; }
    private static String groupKey(Object value){ String key=Objects.toString(value,"").trim(); return key.isBlank()?"Sem valor":key; }
    private static BigDecimal decimal(String value){ try { return new BigDecimal(value.replace(',','.')); } catch(Exception e){ return BigDecimal.ZERO; } }
    private static String type(Object value){ return value instanceof Number?"NUMBER":value instanceof LocalDate?"DATE":"TEXT"; }
    private static String label(String key){ if(key==null||key.isBlank()) return "Atributo"; String words=key.replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' '); return words.substring(0,1).toUpperCase(Locale.ROOT)+words.substring(1); }
}
