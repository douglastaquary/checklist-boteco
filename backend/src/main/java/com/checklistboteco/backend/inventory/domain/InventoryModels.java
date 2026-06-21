package com.checklistboteco.backend.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class InventoryModels {
    private InventoryModels() {}
    public enum ProductCategory { ALCOOLICO, NAO_ALCOOLICO }
    public enum StorageCondition { GELADO, NATURAL }

    public static class CountItem {
        public String id,name;
        public BigDecimal quantity=BigDecimal.ZERO;
        public ProductCategory category;
        public BigDecimal volume=BigDecimal.ZERO;
        public String volumeUnit="ML";
        public long salePriceInCents;
        public Long costPriceInCents;
        public StorageCondition condition;
    }
    public static class CountSession {
        public String id,createdBy,createdByName,location="Beco da Praia",status="SUBMITTED";
        public LocalDate countDate;
        public Instant countedAt,submittedAt;
        public List<CountItem> items=new ArrayList<>();
    }
    public static class SubmitCountRequest {
        public LocalDate countDate;
        public Instant countedAt;
        public String location="Beco da Praia";
        public List<CountItem> items=new ArrayList<>();
    }
    public static class DailyAuditRequest {
        public LocalDate date;
        public String location="Beco da Praia",text;
    }
    public static class DailyAuditItem {
        public String product,category,location,status,notes;
        public BigDecimal openingQuantity=BigDecimal.ZERO,soldQuantity=BigDecimal.ZERO,theoreticalRemaining=BigDecimal.ZERO;
        public long projectedRevenueInCents,projectedCostInCents;
    }
    public static class DailyAuditResponse {
        public LocalDate date;
        public String location="Beco da Praia";
        public List<DailyAuditItem> items=new ArrayList<>();
        public BigDecimal totalOpening=BigDecimal.ZERO,totalSold=BigDecimal.ZERO,totalRemaining=BigDecimal.ZERO;
        public long projectedRevenueInCents;
    }

    public static class AdminStockSession {
        public String id,createdBy,createdByName,location="Beco da Praia",status="SUBMITTED";
        public LocalDate countDate;
        public Instant countedAt,submittedAt;
        public List<CountItem> items=new ArrayList<>();
    }

    public static class AdminStockBalance {
        public String productKey,productName,location="Beco da Praia";
        public BigDecimal quantity=BigDecimal.ZERO;
        public Instant updatedAt;
    }

    public static class ApplyDailyAuditResponse {
        public DailyAuditResponse audit;
        public List<AdminStockBalance> balances=new ArrayList<>();
        public boolean alreadyApplied;
    }
}
