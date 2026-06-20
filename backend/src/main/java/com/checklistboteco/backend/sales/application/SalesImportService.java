package com.checklistboteco.backend.sales.application;

import static com.checklistboteco.backend.sales.domain.SalesModels.*;

import com.checklistboteco.backend.model.Models.User;
import com.checklistboteco.backend.sales.csv.CsvSupport;
import com.checklistboteco.backend.sales.persistence.SalesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class SalesImportService {
    private static final String DEFAULT_LOCATION="Beco da Praia";
    private static final Map<String,List<String>> ALIASES=Map.of(
        "saleDate",List.of("data","data_venda","dt_venda","emissao","date","data_movimento","dia","dt","data_do_item","data_item","data_da_venda"),
        "description",List.of("produto","item","mercadoria","descricao","description","nome"),
        "category",List.of("categoria","grupo","departamento","category"),
        "location",List.of("local","loja","unidade","pdv","location"),
        "quantity",List.of("quantidade","qtd","qtde","quantity"),
        "totalInCents",List.of("total","valor_total","valor","receita","faturamento","total_venda","valor_venda"),
        "documentNumber",List.of("cupom","pedido","documento","numero_documento","cod_produto","codigo_produto"),
        "unit",List.of("unidade","un","unit","tipo_preco"),
        "unitPriceInCents",List.of("valor_unitario","preco_unitario","ticket_medio","unit_price","val_unit","vl_unit","val_unitario")
    );
    @Inject SalesRepository repository;

    public ImportBatch preview(User admin,PreviewRequest request){
        require(request!=null&&request.csv!=null&&!request.csv.isBlank(),"CSV vazio");
        require(request.csv.getBytes(StandardCharsets.UTF_8).length<=2_000_000,"CSV excede o limite de 2 MB");
        char delimiter=CsvSupport.detectDelimiter(request.csv); List<List<String>> rows=CsvSupport.parse(request.csv,delimiter);
        require(rows.size()>=2,"O CSV precisa ter cabeçalho e ao menos uma linha"); require(rows.size()<=10_100,"CSV excede o limite de 10.000 linhas");
        ImportBatch batch=new ImportBatch(); batch.id=UUID.randomUUID().toString(); batch.fileName=cleanFileName(request.fileName);
        batch.fileHash=hash(request.csv); batch.createdBy=admin.id; batch.createdAt=Instant.now(); batch.delimiter=delimiter; batch.rawCsv=request.csv;
        int headerIndex=findHeader(rows); require(headerIndex>=0,"Não foi possível localizar um cabeçalho com produto e quantidade no CSV de vendas");
        batch.referenceYear=referenceYear(request.csv);
        PreparedRows prepared=prepareRows(rows,headerIndex,batch.referenceYear);
        batch.headers=uniqueHeaders(prepared.header()); batch.suggestedMapping=suggest(batch.headers);
        for(List<String> row:prepared.rows()){
            if(isHeader(row)||isSummary(row)) continue;
            Map<String,String> source=asMap(batch.headers,row);
            if(!hasDataValues(source,batch.suggestedMapping)) continue;
            batch.rows.add(source);
        }
        require(!batch.rows.isEmpty(),"Nenhuma linha de venda foi encontrada no CSV"); require(batch.rows.size()<=10_000,"CSV excede o limite de 10.000 linhas");
        List<String> emptyHeaders=batch.headers.stream().filter(header->batch.rows.stream().allMatch(row->row.getOrDefault(header,"").isBlank())).toList();
        batch.headers.removeAll(emptyHeaders); batch.rows.forEach(row->emptyHeaders.forEach(row::remove));
        deduplicateColumns(batch);
        batch.suggestedMapping=suggest(batch.headers);
        batch.totalRows=batch.rows.size(); for(int i=0;i<Math.min(batch.rows.size(),5);i++) batch.sampleRows.add(new LinkedHashMap<>(batch.rows.get(i)));
        if(requiresExplicitDate(batch.headers)&&!batch.suggestedMapping.containsKey("saleDate")) batch.errors.add(new ImportError(1,"saleDate","Mapeie a coluna de data da venda"));
        if(!batch.suggestedMapping.containsKey("description")) batch.errors.add(new ImportError(1,"description","Mapeie a coluna do produto vendido"));
        if(!batch.suggestedMapping.containsKey("quantity")) batch.errors.add(new ImportError(1,"quantity","Mapeie a coluna de quantidade"));
        repository.saveBatch(batch); return publicBatch(batch);
    }

    public ImportBatch commit(User admin,String id,CommitRequest request){
        ImportBatch batch=repository.getBatch(id); require(batch!=null,"Importação não encontrada"); require("PREVIEW".equals(batch.status),"Importação já processada");
        Map<String,String> mapping=request!=null&&request.mapping!=null?request.mapping:batch.suggestedMapping;
        require(mapping.containsKey("description")&&mapping.containsKey("quantity"),"Produto e quantidade são obrigatórios");
        if(requiresExplicitDate(batch.headers)) require(mapping.containsKey("saleDate"),"Esse CSV possui coluna de data. Mapeie a data da venda antes de importar");
        for(String header:mapping.values()) require(batch.headers.contains(header),"Coluna mapeada não existe: "+header);
        String dataset=request==null||request.datasetId==null||request.datasetId.isBlank()?"sales":request.datasetId.trim();
        Set<String> preserve=request==null||request.preserveColumns==null||request.preserveColumns.isEmpty()?new LinkedHashSet<>(batch.headers):new LinkedHashSet<>(request.preserveColumns);
        preserve=sanitizePreserveColumns(batch,preserve,mapping);
        batch.mapping=new LinkedHashMap<>(mapping); batch.datasetId=dataset; batch.errors.clear();
        for(int index=0;index<batch.rows.size();index++){
            Map<String,String> source=batch.rows.get(index);
            try {
                Sale sale=toSale(batch,source,mapping,preserve);
                if(repository.saveIfAbsent(sale)) batch.importedRows++; else batch.duplicateRows++;
            } catch(IllegalArgumentException e){ batch.rejectedRows++; batch.errors.add(new ImportError(index+1,"row",e.getMessage())); }
        }
        batch.status=batch.rejectedRows==batch.totalRows?"FAILED":"COMMITTED"; batch.rawCsv=null; repository.saveBatch(batch); return publicBatch(batch);
    }

    public ImportBatch get(String id){ ImportBatch batch=repository.getBatch(id); require(batch!=null,"Importação não encontrada"); return publicBatch(batch); }
    public List<ImportBatch> list(){ return repository.batches().stream().map(this::publicBatch).toList(); }

    private Sale toSale(ImportBatch batch,Map<String,String> source,Map<String,String> mapping,Set<String> preserve){
        Sale sale=new Sale(); sale.id=UUID.randomUUID().toString(); sale.datasetId=batch.datasetId; sale.importId=batch.id; sale.importedAt=Instant.now();
        String date=value(source,mapping,"saleDate"); sale.saleDate=date.isBlank()?defaultSaleDate(batch):parseDate(date,batch.referenceYear);
        sale.description=required(value(source,mapping,"description"),"Produto vendido vazio");
        sale.location=defaultLocation(value(source,mapping,"location"));
        sale.category=nullable(value(source,mapping,"category"));
        sale.documentNumber=nullable(value(source,mapping,"documentNumber"));
        sale.unit=nullable(value(source,mapping,"unit"));
        sale.quantity=parseDecimal(required(value(source,mapping,"quantity"),"Quantidade vazia"));
        String total=value(source,mapping,"totalInCents"); if(!total.isBlank()) sale.totalInCents=parseMoney(total);
        String unitPrice=value(source,mapping,"unitPriceInCents"); if(!unitPrice.isBlank()) sale.unitPriceInCents=parseMoney(unitPrice);
        if(sale.totalInCents==0&&sale.unitPriceInCents>0&&sale.quantity!=null) sale.totalInCents=toCents(sale.quantity.multiply(BigDecimal.valueOf(sale.unitPriceInCents,2)));
        if(sale.unitPriceInCents==0&&sale.totalInCents>0&&sale.quantity!=null&&sale.quantity.compareTo(BigDecimal.ZERO)>0){
            sale.unitPriceInCents=toCents(BigDecimal.valueOf(sale.totalInCents,2).divide(sale.quantity,4,RoundingMode.HALF_UP));
        }
        for(String header:preserve){ String raw=source.getOrDefault(header,""); if(!raw.isBlank()) sale.attributes.put(uniqueAttributeKey(sale.attributes,CsvSupport.key(header)),infer(raw)); }
        sale.rowHash=hash(sale.datasetId+"|"+source);
        return sale;
    }

    private static Map<String,String> suggest(List<String> headers){
        Map<String,String> result=new LinkedHashMap<>();
        for(var entry:ALIASES.entrySet()) for(String header:headers) if(entry.getValue().contains(CsvSupport.key(header))){ result.put(entry.getKey(),header); break; }
        return result;
    }
    private static void deduplicateColumns(ImportBatch batch){
        if(batch.headers.isEmpty()||batch.rows.isEmpty()) return;
        List<String> deduplicated=new ArrayList<>();
        for(String header:batch.headers){
            String semantic=semanticKey(header);
            String duplicate=deduplicated.stream().filter(current->semantic.equals(semanticKey(current))&&sameColumnValues(batch.rows,current,header)).findFirst().orElse(null);
            if(duplicate==null) deduplicated.add(header);
        }
        if(deduplicated.size()==batch.headers.size()) return;
        Set<String> removed=new LinkedHashSet<>(batch.headers);
        removed.removeAll(deduplicated);
        batch.headers=new ArrayList<>(deduplicated);
        batch.rows.forEach(row->removed.forEach(row::remove));
    }
    private static PreparedRows prepareRows(List<List<String>> rows,int headerIndex,Integer referenceYear){
        List<String> baseHeader=new ArrayList<>(rows.get(headerIndex));
        int baseSize=baseHeader.size();
        boolean headerHasDate=headerContainsDate(baseHeader);
        List<List<String>> prepared=new ArrayList<>();
        String currentDate="";
        for(int i=headerIndex+1;i<rows.size();i++){
            List<String> raw=rows.get(i);
            if(raw==null||raw.stream().allMatch(value->value==null||value.isBlank())) continue;
            List<String> normalized=normalizeWidth(raw,baseSize);
            String first=normalized.isEmpty()?"":normalized.get(0).trim();
            if(isDateMarkerRow(normalized,referenceYear)){
                currentDate=normalizeMarkerDate(first,referenceYear);
                continue;
            }
            if(!headerHasDate){
                List<String> withDate=new ArrayList<>(normalized);
                withDate.add(currentDate);
                prepared.add(withDate);
            } else prepared.add(normalized);
        }
        if(!headerHasDate&&prepared.stream().anyMatch(row->row.size()>baseSize&&!row.get(row.size()-1).isBlank())){
            baseHeader=new ArrayList<>(baseHeader);
            baseHeader.add("Data da Venda");
        }
        return new PreparedRows(baseHeader,prepared);
    }
    private static int findHeader(List<List<String>> rows){ for(int i=0;i<rows.size();i++) if(isHeader(rows.get(i))) return i; return rows.isEmpty()?-1:0; }
    private static boolean isHeader(List<String> row){
        Set<String> keys=new HashSet<>(); row.forEach(value->keys.add(CsvSupport.key(value)));
        boolean hasDescription=matchesAlias(keys,"description");
        boolean hasQuantity=matchesAlias(keys,"quantity");
        boolean hasUsefulSupport=matchesAlias(keys,"saleDate")||matchesAlias(keys,"location")||matchesAlias(keys,"totalInCents")||matchesAlias(keys,"unitPriceInCents");
        return hasDescription&&hasQuantity&&hasUsefulSupport;
    }
    private static List<String> requiredFields(){ return List.of("description","quantity"); }
    private static boolean hasDataValues(Map<String,String> row,Map<String,String> mapping){ boolean complete=requiredFields().stream().allMatch(mapping::containsKey); return complete?requiredFields().stream().allMatch(field->!value(row,mapping,field).isBlank()):row.values().stream().filter(v->v!=null&&!v.isBlank()).count()>=2; }
    private static boolean matchesAlias(Set<String> keys,String field){ return ALIASES.getOrDefault(field,List.of()).stream().anyMatch(keys::contains); }
    private static boolean headerContainsDate(List<String> header){
        Set<String> keys=new HashSet<>();
        header.forEach(value->keys.add(CsvSupport.key(value)));
        return matchesAlias(keys,"saleDate");
    }
    private static boolean requiresExplicitDate(List<String> headers){
        Set<String> keys=new HashSet<>();
        headers.forEach(value->keys.add(CsvSupport.key(value)));
        return matchesAlias(keys,"saleDate");
    }
    private static List<String> normalizeWidth(List<String> row,int size){
        List<String> normalized=new ArrayList<>(row);
        while(normalized.size()<size) normalized.add("");
        if(normalized.size()>size) normalized=new ArrayList<>(normalized.subList(0,size));
        return normalized;
    }
    private static boolean isDateMarkerRow(List<String> row,Integer referenceYear){
        if(row.isEmpty()) return false;
        String first=row.get(0)==null?"":row.get(0).trim();
        if(first.isBlank()) return false;
        if(!looksLikeDate(first,referenceYear)) return false;
        return row.stream().skip(1).allMatch(value->value==null||value.isBlank());
    }
    private static boolean looksLikeDate(String value,Integer referenceYear){
        String current=value.trim();
        if(current.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}")) return true;
        if(current.matches("\\d{1,2}/\\d{1,2}")) return referenceYear!=null;
        return false;
    }
    private static String normalizeMarkerDate(String value,Integer referenceYear){
        String current=value.trim();
        if(current.matches("\\d{1,2}/\\d{1,2}$")&&referenceYear!=null) return current+"/"+referenceYear;
        return current;
    }
    private static boolean isSummary(List<String> row){ String first=row.stream().map(String::trim).filter(v->!v.isBlank()).findFirst().orElse(""); String key=CsvSupport.key(first); return key.equals("total")||key.equals("total_geral")||key.equals("resumo"); }
    private static Integer referenceYear(String csv){ Matcher matcher=Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)").matcher(csv); return matcher.find()?Integer.valueOf(matcher.group(1)):null; }
    private static List<String> uniqueHeaders(List<String> raw){
        List<String> result=new ArrayList<>(); Map<String,Integer> seen=new HashMap<>();
        for(String value:raw){ String base=value==null||value.isBlank()?"Coluna":value.trim(); int count=seen.merge(base,1,Integer::sum); result.add(count==1?base:base+" ("+count+")"); }
        return result;
    }
    private static Map<String,String> asMap(List<String> headers,List<String> values){ Map<String,String> result=new LinkedHashMap<>(); for(int i=0;i<headers.size();i++) result.put(headers.get(i),i<values.size()?values.get(i):""); return result; }
    private static Set<String> sanitizePreserveColumns(ImportBatch batch,Set<String> preserve,Map<String,String> mapping){
        Set<String> mappedHeaders=new LinkedHashSet<>(mapping.values());
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(String header:preserve){
            if(mappedHeaders.contains(header)) continue;
            boolean duplicatesMapped=mappedHeaders.stream().anyMatch(mapped->semanticKey(mapped).equals(semanticKey(header))&&sameColumnValues(batch.rows,mapped,header));
            if(!duplicatesMapped) result.add(header);
        }
        return result;
    }
    private static boolean sameColumnValues(List<Map<String,String>> rows,String left,String right){
        for(Map<String,String> row:rows){
            String a=Objects.toString(row.get(left),"").trim();
            String b=Objects.toString(row.get(right),"").trim();
            if(!Objects.equals(a,b)) return false;
        }
        return true;
    }
    private static String semanticKey(String header){
        String key=CsvSupport.key(header);
        for(var entry:ALIASES.entrySet()) if(entry.getValue().contains(key)) return entry.getKey();
        return key;
    }
    private static String value(Map<String,String> source,Map<String,String> mapping,String field){ String header=mapping.get(field); return header==null?"":source.getOrDefault(header,"").trim(); }
    private static String required(String value,String message){ if(value==null||value.isBlank()) throw new IllegalArgumentException(message); return value.trim(); }
    private static String nullable(String value){ return value==null||value.isBlank()?null:value.trim(); }
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
    private static long toCents(BigDecimal value){ return value.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValue(); }
    private static Object infer(String raw){ try { return parseDecimal(raw); } catch(Exception ignored) {} try { return parseDate(raw,null).toString(); } catch(Exception ignored) {} return raw.trim(); }
    private static LocalDate defaultSaleDate(ImportBatch batch){ return LocalDate.ofInstant(batch.createdAt==null?Instant.now():batch.createdAt,ZoneId.systemDefault()); }
    private static String defaultLocation(String value){
        String normalized=value==null?"":value.trim();
        if(normalized.isBlank()) return DEFAULT_LOCATION;
        String key=CsvSupport.key(normalized);
        if(key.equals("beco")||key.equals("beco_da_praia")||key.equals("nao_informado")||key.equals("n_a")) return DEFAULT_LOCATION;
        return normalized;
    }
    private static String uniqueAttributeKey(Map<String,Object> attributes,String base){ String key=base; int suffix=2; while(attributes.containsKey(key)) key=base+"_"+suffix++; return key; }
    private static String cleanFileName(String value){ String name=value==null?"vendas.csv":value.replaceAll("[\\r\\n\\\\/]","_"); return name.length()>120?name.substring(0,120):name; }
    private static String hash(String value){
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){ throw new IllegalStateException(e); }
    }
    private static void require(boolean valid,String message){ if(!valid) throw new IllegalArgumentException(message); }
    private record PreparedRows(List<String> header,List<List<String>> rows){}
    private ImportBatch publicBatch(ImportBatch source){
        ImportBatch batch=new ImportBatch(); batch.id=source.id; batch.fileName=source.fileName; batch.fileHash=source.fileHash; batch.datasetId=source.datasetId; batch.createdBy=source.createdBy; batch.status=source.status;
        batch.createdAt=source.createdAt; batch.delimiter=source.delimiter; batch.referenceYear=source.referenceYear; batch.headers=new ArrayList<>(source.headers); batch.suggestedMapping=new LinkedHashMap<>(source.suggestedMapping); batch.mapping=new LinkedHashMap<>(source.mapping);
        batch.sampleRows=new ArrayList<>(source.sampleRows); batch.errors=new ArrayList<>(source.errors); batch.totalRows=source.totalRows; batch.importedRows=source.importedRows; batch.duplicateRows=source.duplicateRows; batch.rejectedRows=source.rejectedRows; return batch;
    }
}
