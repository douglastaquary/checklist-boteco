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
        public String description,category,location,documentNumber,unit,seller;
        public BigDecimal quantity=BigDecimal.ZERO;
        public long unitPriceInCents,totalInCents,serviceChargeInCents;
        public Map<String,Object> attributes=new LinkedHashMap<>();
        public String importId,rowHash,saleFingerprint;
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
        public List<String> validationWarnings=new ArrayList<>();
        public LocalDate coverageFrom,coverageTo;
        public int totalRows,importedRows,duplicateRows,rejectedRows,newRows,inFileDuplicateRows,existingDuplicateRows,missingDateRows;
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

    public static class PreviewRequest { public String fileName,csv,datasetId="sales"; }
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
        public List<String> sellers=new ArrayList<>();
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
        public long serviceChargeInCents;
        public BigDecimal totalQuantity=BigDecimal.ZERO;
        public List<String> filtersApplied=new ArrayList<>();
        public String currency="BRL";
    }
    public static class AggregateRequest extends SaleQuery { public String groupBy="category"; }
    public static class AggregateBucket {
        public String key;
        public long count,totalInCents,serviceChargeInCents;
        public BigDecimal quantity=BigDecimal.ZERO;
    }
    public static class AggregateResponse {
        public String groupBy,currency="BRL";
        public List<AggregateBucket> groups=new ArrayList<>();
        public long totalInCents,serviceChargeInCents,totalItems;
        public BigDecimal totalQuantity=BigDecimal.ZERO;
    }

    public static class SalesHeatmapDay {
        public String date;
        public BigDecimal quantity=BigDecimal.ZERO;
        public long totalInCents;
        public SalesHeatmapDay() {}
        public SalesHeatmapDay(String date, BigDecimal quantity, long totalInCents){
            this.date=date;
            this.quantity=quantity==null?BigDecimal.ZERO:quantity;
            this.totalInCents=totalInCents;
        }
    }

    public static class SalesHeatmapResponse {
        public int year;
        public String datasetId="sales";
        public List<SalesHeatmapDay> days=new ArrayList<>();
    }

    public static class MonthCompareRequest extends SaleQuery {
        public String focusMonth;
        public int topProducts=10;
    }
    public static class PeriodSnapshot {
        public String label;
        public long totalInCents,serviceChargeInCents,lineCount,averageDailyRevenueInCents,averageLineRevenueInCents;
        public BigDecimal totalQuantity=BigDecimal.ZERO;
        public int daysWithSales;
        public double weekendRevenueSharePercent;
    }
    public static class MonthDelta {
        public long revenueInCents,lineCount,averageDailyRevenueInCents,averageLineRevenueInCents;
        public BigDecimal quantity=BigDecimal.ZERO;
        public double revenuePercent,quantityPercent,lineCountPercent,averageDailyRevenuePercent,averageLineRevenuePercent,weekendSharePoints;
    }
    public static class WeekdayComparison {
        public String dayOfWeek,label;
        public long focusRevenueInCents,baselineAverageRevenueInCents;
        public double focusSharePercent,baselineSharePercent,shareDeltaPoints;
    }
    public static class ProductDriver {
        public String product;
        public long focusRevenueInCents,baselineAverageRevenueInCents,revenueDeltaInCents;
        public BigDecimal focusQuantity=BigDecimal.ZERO,baselineAverageQuantity=BigDecimal.ZERO;
        public double focusRevenueSharePercent,baselineRevenueSharePercent,shareDeltaPoints;
    }
    public static class TopSalesDay {
        public LocalDate date;
        public String dayOfWeek,label;
        public long totalInCents,lineCount;
        public BigDecimal quantity=BigDecimal.ZERO;
    }
    public static class MonthCompareResponse {
        public String datasetId="sales",focusMonth,baselineLabel,currency="BRL";
        public LocalDate sourceCoverageFrom,sourceCoverageTo;
        public List<String> baselineMonths=new ArrayList<>();
        public PeriodSnapshot focus=new PeriodSnapshot(),baselineAverage=new PeriodSnapshot();
        public MonthDelta delta=new MonthDelta();
        public List<WeekdayComparison> weekdays=new ArrayList<>();
        public List<ProductDriver> topProductDrivers=new ArrayList<>();
        public List<TopSalesDay> topDays=new ArrayList<>();
        public List<String> findings=new ArrayList<>(),filtersApplied=new ArrayList<>();
        public String caveat="Os fatores indicam associações nos dados de venda; não comprovam causalidade externa.";
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

    public static class SellerSearchRequest extends SaleQuery {
        public String seller;
        public int limit=50;
    }
    public static class SellerSalesMatch {
        public String seller,location;
        public long salesCount,totalInCents,serviceChargeInCents;
        public BigDecimal quantity=BigDecimal.ZERO;
    }
    public static class SellerSalesResponse {
        public String datasetId="sales",seller,currency="BRL";
        public LocalDate from,to;
        public List<String> locations=new ArrayList<>();
        public List<String> filtersApplied=new ArrayList<>();
        public List<SellerSalesMatch> items=new ArrayList<>();
        public long totalItems,totalInCents,serviceChargeInCents;
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
