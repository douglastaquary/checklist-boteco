package com.checklistboteco.backend.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class SalesModels {
    private SalesModels() {}

    public static class Sale {
        public String id;
        public String datasetId="sales";
        public LocalDate saleDate;
        public String description,category,location,documentNumber,unit;
        public BigDecimal quantity=BigDecimal.ZERO;
        public long unitPriceInCents,totalInCents;
        public Map<String,Object> attributes=new LinkedHashMap<>();
        public String importId,rowHash;
        public Instant importedAt;
    }

    public static class ImportBatch {
        public String id,fileName,fileHash,datasetId="sales",createdBy,status="PREVIEW";
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
        public String datasetId="sales";
        public List<SchemaField> fields=new ArrayList<>();
        public LocalDate coverageFrom,coverageTo;
        public long saleCount;
    }

    public static class PreviewRequest { public String fileName,csv; }
    public static class CommitRequest {
        public String datasetId="sales";
        public Map<String,String> mapping=new LinkedHashMap<>();
        public List<String> preserveColumns=new ArrayList<>();
    }

    public static class AttributeCondition { public String operator="EQUALS"; public Object value,to; }
    public static class SortField { public String field="saleDate",direction="DESC"; }
    public static class SaleQuery {
        public LocalDate from,to;
        public List<String> categories=new ArrayList<>();
        public List<String> locations=new ArrayList<>();
        public Long minTotalInCents,maxTotalInCents;
        public String text;
        public Map<String,AttributeCondition> attributes=new LinkedHashMap<>();
        public List<SortField> sort=new ArrayList<>();
        public int page=0,pageSize=50;
    }
    public static class SalePage {
        public List<Sale> items=new ArrayList<>();
        public int page,pageSize,totalPages;
        public long totalItems,totalInCents;
        public BigDecimal totalQuantity=BigDecimal.ZERO;
        public List<String> filtersApplied=new ArrayList<>();
        public String currency="BRL";
    }
    public static class AggregateRequest extends SaleQuery { public String groupBy="category"; }
    public static class AggregateBucket {
        public String key;
        public long count,totalInCents;
        public BigDecimal quantity=BigDecimal.ZERO;
    }
    public static class AggregateResponse {
        public String groupBy,currency="BRL";
        public List<AggregateBucket> groups=new ArrayList<>();
        public long totalInCents,totalItems;
        public BigDecimal totalQuantity=BigDecimal.ZERO;
    }

    public static class ProductSearchRequest extends SaleQuery {
        public String product;
        public int limit=20;
    }
    public static class ProductMatch {
        public String description,category,location;
        public long salesCount,totalInCents;
        public BigDecimal quantity=BigDecimal.ZERO;
    }
    public static class ProductSearchResponse {
        public String datasetId="sales",product,currency="BRL";
        public LocalDate from,to;
        public List<String> locations=new ArrayList<>();
        public List<String> filtersApplied=new ArrayList<>();
        public List<ProductMatch> items=new ArrayList<>();
        public long totalItems,totalInCents;
        public BigDecimal totalQuantity=BigDecimal.ZERO;
    }

    public static class SalesAuditRequest {
        public String purchaseDatasetId="purchases";
        public String salesDatasetId="sales";
        public LocalDate from,to;
        public List<String> locations=new ArrayList<>();
        public String text;
        public int limit=200;
    }
    public static class SalesAuditRow {
        public String productKey,description,category,location,status,notes;
        public BigDecimal stockedQuantity=BigDecimal.ZERO,soldQuantity=BigDecimal.ZERO,differenceQuantity=BigDecimal.ZERO;
        public long stockedRecords,soldRecords;
    }
    public static class SalesAuditResponse {
        public String purchaseDatasetId="purchases",salesDatasetId="sales";
        public LocalDate from,to;
        public List<String> filtersApplied=new ArrayList<>();
        public List<SalesAuditRow> items=new ArrayList<>();
        public long totalItems;
    }
}
