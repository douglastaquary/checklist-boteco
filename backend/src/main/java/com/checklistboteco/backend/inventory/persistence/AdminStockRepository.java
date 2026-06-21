package com.checklistboteco.backend.inventory.persistence;

import com.checklistboteco.backend.inventory.domain.InventoryModels.AdminStockBalance;
import com.checklistboteco.backend.inventory.domain.InventoryModels.AdminStockSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AdminStockRepository {
    void saveSession(AdminStockSession session);
    List<AdminStockSession> listSessions();
    List<AdminStockBalance> listBalances();
    AdminStockBalance adjustBalance(String productKey, String productName, String location, java.math.BigDecimal delta);
    Optional<java.time.Instant> appliedAuditAt(LocalDate date, String location);
    void markAuditApplied(LocalDate date, String location, java.time.Instant appliedAt, String appliedBy);
}
