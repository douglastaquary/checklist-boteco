package com.checklistboteco.backend.purchases.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class PurchaseModels {
    private PurchaseModels() {}

    public static class Purchase {
        public String id;
        public String datasetId="purchases";
        public LocalDate purchaseDate;
        public String description,category,location,supplier,documentNumber,unit;
        public BigDecimal quantity=BigDecimal.ZERO;
        public long unitPriceInCents,totalInCents;
        public Map<String,Object> attributes=new LinkedHashMap<>();
        public String importId,rowHash;
        public Instant importedAt;
    }

    public static class ImportBatch {
        public String id,fileName,fileHash,datasetId="purchases",createdBy,status="PREVIEW";
        public Instant createdAt;
        public char delimiter=',';
        public String rawCsv;
        public Integer referenceYear;
        public List<String> headers=new ArrayList<>();
        public List<Map<String,String>> rows=new ArrayList<>();
        public Map<String,String> suggestedMapping=new LinkedHashMap<>();
        public Map<String,String> mapping=new LinkedHashMap<>();
        public List<Map<String,String>> sampleRows=new ArrayList<>();
        public List<ImportError> errors=new ArrayList<>();
        public int totalRows,importedRows,duplicateRows,rejectedRows;
    }

    public static class ImportError {
        public int row;
        public String field,message;
        public ImportError() {}
        public ImportError(int row,String field,String message){ this.row=row; this.field=field; this.message=message; }
    }

    public static class SchemaField {
        public String key,label,type;
        public boolean normalized,filterable=true;
        public SchemaField() {}
        public SchemaField(String key,String label,String type,boolean normalized){ this.key=key; this.label=label; this.type=type; this.normalized=normalized; }
    }

    public static class ImportSchema {
        public String datasetId="purchases";
        public List<SchemaField> fields=new ArrayList<>();
        public LocalDate coverageFrom,coverageTo;
        public long purchaseCount;
    }

    public static class PreviewRequest { public String fileName,csv; }
    public static class CommitRequest {
        public String datasetId="purchases";
        public Map<String,String> mapping=new LinkedHashMap<>();
        public List<String> preserveColumns=new ArrayList<>();
    }

    public static class ReceiptSessionItem {
        public String description,category;
        public BigDecimal quantity=BigDecimal.ONE;
        public long unitPriceInCents,totalInCents;
    }

    public static class ReceiptSessionSubmitRequest {
        public String datasetId="purchases";
        public LocalDate purchaseDate;
        public String location="Beco da Praia";
        public String supplier,paymentMethod,documentNumber;
        public List<ReceiptSessionItem> items=new ArrayList<>();
    }

    public static class ReceiptSessionSubmitResponse {
        public String sessionId,status="COMMITTED";
        public int importedRows,duplicateRows,rejectedRows;
        public long totalInCents;
        public List<ImportError> errors=new ArrayList<>();
    }

    public static class AttributeCondition { public String operator="EQUALS"; public Object value,to; }
    public static class SortField { public String field="purchaseDate",direction="DESC"; }
    public static class PurchaseQuery {
        public LocalDate from,to;
        public List<String> categories=new ArrayList<>(),suppliers=new ArrayList<>();
        public List<String> locations=new ArrayList<>();
        public Long minTotalInCents,maxTotalInCents;
        public String text;
        public Map<String,AttributeCondition> attributes=new LinkedHashMap<>();
        public List<SortField> sort=new ArrayList<>();
        public int page=0,pageSize=50;
    }
    public static class PurchasePage {
        public List<Purchase> items=new ArrayList<>();
        public int page,pageSize,totalPages;
        public long totalItems,totalInCents;
        public List<String> filtersApplied=new ArrayList<>();
        public String currency="BRL";
    }
    public static class AggregateRequest extends PurchaseQuery { public String groupBy="category"; }
    public static class AggregateBucket {
        public String key;
        public long count,totalInCents;
        public BigDecimal quantity=BigDecimal.ZERO;
    }
    public static class AggregateResponse {
        public String groupBy,currency="BRL";
        public List<AggregateBucket> groups=new ArrayList<>();
        public long totalInCents,totalItems;
    }
}
