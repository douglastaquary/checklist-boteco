package com.checklistboteco.backend.inventory.persistence;

import com.checklistboteco.backend.inventory.domain.InventoryModels.CountSession;
import java.util.List;

public interface InventoryRepository {
    void save(CountSession session);
    List<CountSession> list();
    void delete(String id);
}
