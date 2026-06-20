package com.checklistboteco.backend.purchases.persistence;

import com.checklistboteco.backend.purchases.domain.PurchaseModels.ImportBatch;
import com.checklistboteco.backend.purchases.domain.PurchaseModels.Purchase;
import java.util.List;

public interface PurchaseRepository {
    void saveBatch(ImportBatch batch);
    ImportBatch getBatch(String id);
    List<ImportBatch> batches();
    boolean saveIfAbsent(Purchase purchase);
    List<Purchase> purchases(String datasetId);
}
