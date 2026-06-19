package com.checklistboteco.backend.sales.persistence;

import com.checklistboteco.backend.sales.domain.SalesModels.ImportBatch;
import com.checklistboteco.backend.sales.domain.SalesModels.Sale;
import java.util.List;

public interface SalesRepository {
    void saveBatch(ImportBatch batch);
    ImportBatch getBatch(String id);
    List<ImportBatch> batches();
    boolean saveIfAbsent(Sale sale);
    List<Sale> sales(String datasetId);
}
