package com.checklistboteco.backend.purchases.application;

import static com.checklistboteco.backend.purchases.domain.PurchaseModels.*;

import com.checklistboteco.backend.purchases.csv.CsvSupport;
import com.checklistboteco.backend.purchases.persistence.PurchaseRepository;
import com.checklistboteco.backend.model.Models.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

@ApplicationScoped
public class PurchaseImportService {
    private static final Map<String,List<String>> ALIASES=Map.of(
        "purchaseDate",List.of("data","data_compra","emissao","dt_compra","purchase_date"),
        "description",List.of("produto","mercadoria","descricao","item","description"),
        "totalInCents",List.of("total","valor_total","vl_total","amount","valor"),
        "category",List.of("categoria","grupo","departamento","category"),
        "location",List.of("local","loja","unidade_local","location","estabelecimento"),
        "supplier",List.of("fornecedor","razao_social","supplier"),
        "documentNumber",List.of("nota","nf","documento","numero_documento"),
        "quantity",List.of("quantidade","qtd","quantity"),
        "unit",List.of("unidade","un","unit"),
        "unitPriceInCents",List.of("valor_unitario","preco","unit_price")
    );
    @Inject PurchaseRepository repository;

    public ImportBatch preview(User admin,PreviewRequest request){
        require(request!=null&&request.csv!=null&&!request.csv.isBlank(),"CSV vazio");
        require(request.csv.getBytes(StandardCharsets.UTF_8).length<=2_000_000,"CSV excede o limite de 2 MB");
        char delimiter=CsvSupport.detectDelimiter(request.csv); List<List<String>> rows=CsvSupport.parse(request.csv,delimiter);
        require(rows.size()>=2,"O CSV precisa ter cabeçalho e ao menos uma linha"); require(rows.size()<=10_100,"CSV excede o limite de 10.000 linhas");
        ImportBatch batch=new ImportBatch(); batch.id=UUID.randomUUID().toString(); batch.fileName=cleanFileName(request.fileName);
        batch.fileHash=hash(request.csv); batch.createdBy=admin.id; batch.createdAt=Instant.now(); batch.delimiter=delimiter; batch.rawCsv=request.csv;
        int headerIndex=findHeader(rows); require(headerIndex>=0,"Não foi possível localizar um cabeçalho com data, categoria, local e valor");
        batch.headers=uniqueHeaders(rows.get(headerIndex)); batch.suggestedMapping=suggest(batch.headers); batch.referenceYear=referenceYear(request.csv);
        String week=findWeek(rows,0,headerIndex+1);
        for(int i=headerIndex+1;i<rows.size();i++){
            List<String> row=rows.get(i); String marker=week(row); if(marker!=null){ week=marker; continue; }
            if(isHeader(row)||isSummary(row)) continue;
            Map<String,String> source=asMap(batch.headers,row);
            if(!hasDataValues(source,batch.suggestedMapping)) continue;
            if(week!=null){ if(!batch.headers.contains("Semana")) batch.headers.add("Semana"); source.put("Semana",week); }
            batch.rows.add(source);
        }
        require(!batch.rows.isEmpty(),"Nenhuma linha de compra foi encontrada no CSV"); require(batch.rows.size()<=10_000,"CSV excede o limite de 10.000 linhas");
        List<String> emptyHeaders=batch.headers.stream().filter(header->batch.rows.stream().allMatch(row->row.getOrDefault(header,"").isBlank())).toList();
        batch.headers.removeAll(emptyHeaders); batch.rows.forEach(row->emptyHeaders.forEach(row::remove));
        batch.totalRows=batch.rows.size(); for(int i=0;i<Math.min(batch.rows.size(),5);i++) batch.sampleRows.add(new LinkedHashMap<>(batch.rows.get(i)));
        if(!batch.suggestedMapping.containsKey("purchaseDate")) batch.errors.add(new ImportError(1,"purchaseDate","Mapeie a coluna de data"));
        if(!batch.suggestedMapping.containsKey("category")) batch.errors.add(new ImportError(1,"category","Mapeie a coluna de categoria"));
        if(!batch.suggestedMapping.containsKey("location")) batch.errors.add(new ImportError(1,"location","Mapeie a coluna de local"));
        if(!batch.suggestedMapping.containsKey("totalInCents")) batch.errors.add(new ImportError(1,"totalInCents","Mapeie a coluna de valor"));
        repository.saveBatch(batch); return publicBatch(batch);
    }

    public ImportBatch commit(User admin,String id,CommitRequest request){
        ImportBatch batch=repository.getBatch(id); require(batch!=null,"Importação não encontrada"); require("PREVIEW".equals(batch.status),"Importação já processada");
        Map<String,String> mapping=request!=null&&request.mapping!=null?request.mapping:batch.suggestedMapping;
        require(mapping.containsKey("purchaseDate")&&mapping.containsKey("category")&&mapping.containsKey("location")&&mapping.containsKey("totalInCents"),"Data, categoria, local e valor são obrigatórios");
        for(String header:mapping.values()) require(batch.headers.contains(header),"Coluna mapeada não existe: "+header);
        String dataset=request==null||request.datasetId==null||request.datasetId.isBlank()?"purchases":request.datasetId.trim();
        Set<String> preserve=request==null||request.preserveColumns==null||request.preserveColumns.isEmpty()?new LinkedHashSet<>(batch.headers):new LinkedHashSet<>(request.preserveColumns);
        batch.mapping=new LinkedHashMap<>(mapping); batch.datasetId=dataset; batch.errors.clear();
        for(int index=0;index<batch.rows.size();index++){
            Map<String,String> source=batch.rows.get(index);
            try {
                Purchase purchase=toPurchase(batch,source,mapping,preserve);
                if(repository.saveIfAbsent(purchase)) batch.importedRows++; else batch.duplicateRows++;
            } catch(IllegalArgumentException e){ batch.rejectedRows++; batch.errors.add(new ImportError(index+1,"row",e.getMessage())); }
        }
        batch.status=batch.rejectedRows==batch.totalRows?"FAILED":"COMMITTED"; batch.rawCsv=null; repository.saveBatch(batch); return publicBatch(batch);
    }

    public ImportBatch get(String id){ ImportBatch batch=repository.getBatch(id); require(batch!=null,"Importação não encontrada"); return publicBatch(batch); }
    public List<ImportBatch> list(){ return repository.batches().stream().map(this::publicBatch).toList(); }

    private Purchase toPurchase(ImportBatch batch,Map<String,String> source,Map<String,String> mapping,Set<String> preserve){
        Purchase p=new Purchase(); p.id=UUID.randomUUID().toString(); p.datasetId=batch.datasetId; p.importId=batch.id; p.importedAt=Instant.now();
        String date=value(source,mapping,"purchaseDate"); if(!date.isBlank()) p.purchaseDate=parseDate(date,batch.referenceYear);
        p.description=nullable(value(source,mapping,"description"));
        String total=value(source,mapping,"totalInCents"); if(!total.isBlank()) p.totalInCents=parseMoney(total);
        p.category=required(value(source,mapping,"category"),"Categoria vazia"); p.location=required(value(source,mapping,"location"),"Local vazio"); p.supplier=nullable(value(source,mapping,"supplier"));
        p.documentNumber=nullable(value(source,mapping,"documentNumber")); p.unit=nullable(value(source,mapping,"unit"));
        String quantity=value(source,mapping,"quantity"); if(!quantity.isBlank()) p.quantity=parseDecimal(quantity);
        String unitPrice=value(source,mapping,"unitPriceInCents"); if(!unitPrice.isBlank()) p.unitPriceInCents=parseMoney(unitPrice);
        for(String header:preserve){ String raw=source.getOrDefault(header,""); if(!raw.isBlank()) p.attributes.put(uniqueAttributeKey(p.attributes,CsvSupport.key(header)),infer(raw)); }
        p.rowHash=hash(p.datasetId+"|"+source);
        return p;
    }

    private static Map<String,String> suggest(List<String> headers){
        Map<String,String> result=new LinkedHashMap<>();
        for(var entry:ALIASES.entrySet()) for(String header:headers) if(entry.getValue().contains(CsvSupport.key(header))){ result.put(entry.getKey(),header); break; }
        return result;
    }
    private static int findHeader(List<List<String>> rows){ for(int i=0;i<rows.size();i++) if(isHeader(rows.get(i))) return i; return rows.isEmpty()?-1:0; }
    private static boolean isHeader(List<String> row){ Set<String> keys=new HashSet<>(); row.forEach(value->keys.add(CsvSupport.key(value))); return coreFields().stream().allMatch(field->ALIASES.get(field).stream().anyMatch(keys::contains)); }
    private static List<String> coreFields(){ return List.of("purchaseDate","category","location","totalInCents"); }
    private static boolean hasDataValues(Map<String,String> row,Map<String,String> mapping){ boolean complete=coreFields().stream().allMatch(mapping::containsKey); return complete?coreFields().stream().allMatch(field->!value(row,mapping,field).isBlank()):row.values().stream().filter(v->v!=null&&!v.isBlank()).count()>=2; }
    private static boolean isSummary(List<String> row){ String first=row.stream().map(String::trim).filter(v->!v.isBlank()).findFirst().orElse(""); String key=CsvSupport.key(first); return key.equals("total")||key.equals("gasto_mensal"); }
    private static String findWeek(List<List<String>> rows,int from,int to){ String result=null; for(int i=from;i<Math.min(to,rows.size());i++){ String value=week(rows.get(i)); if(value!=null) result=value; } return result; }
    private static String week(List<String> row){ for(String value:row) if(CsvSupport.key(value).matches("[0-9]+_semana")) return value.trim(); return null; }
    private static Integer referenceYear(String csv){ Matcher matcher=Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)").matcher(csv); return matcher.find()?Integer.valueOf(matcher.group(1)):null; }
    private static List<String> uniqueHeaders(List<String> raw){
        List<String> result=new ArrayList<>(); Map<String,Integer> seen=new HashMap<>();
        for(String value:raw){ String base=value==null||value.isBlank()?"Coluna":value.trim(); int count=seen.merge(base,1,Integer::sum); result.add(count==1?base:base+" ("+count+")"); }
        return result;
    }
    private static Map<String,String> asMap(List<String> headers,List<String> values){ Map<String,String> result=new LinkedHashMap<>(); for(int i=0;i<headers.size();i++) result.put(headers.get(i),i<values.size()?values.get(i):""); return result; }
    private static String value(Map<String,String> source,Map<String,String> mapping,String field){ String header=mapping.get(field); return header==null?"":source.getOrDefault(header,"").trim(); }
    private static String required(String value,String message){ if(value==null||value.isBlank()) throw new IllegalArgumentException(message); return value.trim(); }
    private static String nullable(String value){ return value==null||value.isBlank()?null:value.trim(); }
    private static LocalDate parseDate(String raw){ return parseDate(raw,null); }
    private static LocalDate parseDate(String raw,Integer referenceYear){
        if(referenceYear!=null&&raw.trim().matches("\\d{1,2}/\\d{1,2}")){
            try { return LocalDate.parse(raw.trim()+"/"+referenceYear,DateTimeFormatter.ofPattern("d/M/uuuu")); } catch(DateTimeParseException ignored) {}
        }
        for(DateTimeFormatter formatter:List.of(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("dd/MM/uuuu"),DateTimeFormatter.ofPattern("dd-MM-uuuu"),DateTimeFormatter.ofPattern("MM/dd/uuuu"))){
            try { return LocalDate.parse(raw.trim(),formatter); } catch(DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Data inválida: "+raw);
    }
    private static BigDecimal parseDecimal(String raw){
        String value=raw.trim().replace("R$","").replace(" ","");
        if(value.contains(",")&&value.contains(".")){ if(value.lastIndexOf(',')>value.lastIndexOf('.')) value=value.replace(".","").replace(',','.'); else value=value.replace(",",""); }
        else if(value.contains(",")) value=value.replace(".","").replace(',','.');
        try { return new BigDecimal(value); } catch(NumberFormatException e){ throw new IllegalArgumentException("Número inválido: "+raw); }
    }
    private static long parseMoney(String raw){ try { return parseDecimal(raw).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact(); } catch(ArithmeticException e){ throw new IllegalArgumentException("Valor monetário inválido: "+raw); } }
    private static Object infer(String raw){ try { return parseDecimal(raw); } catch(Exception ignored) {} try { return parseDate(raw).toString(); } catch(Exception ignored) {} return raw.trim(); }
    private static String uniqueAttributeKey(Map<String,Object> attributes,String base){ String key=base; int suffix=2; while(attributes.containsKey(key)) key=base+"_"+suffix++; return key; }
    private static String cleanFileName(String value){ String name=value==null?"compras.csv":value.replaceAll("[\\r\\n\\\\/]","_"); return name.length()>120?name.substring(0,120):name; }
    private static String hash(String value){
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){ throw new IllegalStateException(e); }
    }
    private static void require(boolean valid,String message){ if(!valid) throw new IllegalArgumentException(message); }
    private ImportBatch publicBatch(ImportBatch source){
        ImportBatch b=new ImportBatch(); b.id=source.id; b.fileName=source.fileName; b.fileHash=source.fileHash; b.datasetId=source.datasetId; b.createdBy=source.createdBy; b.status=source.status;
        b.createdAt=source.createdAt; b.delimiter=source.delimiter; b.referenceYear=source.referenceYear; b.headers=new ArrayList<>(source.headers); b.suggestedMapping=new LinkedHashMap<>(source.suggestedMapping); b.mapping=new LinkedHashMap<>(source.mapping);
        b.sampleRows=new ArrayList<>(source.sampleRows); b.errors=new ArrayList<>(source.errors); b.totalRows=source.totalRows; b.importedRows=source.importedRows; b.duplicateRows=source.duplicateRows; b.rejectedRows=source.rejectedRows; return b;
    }
}
