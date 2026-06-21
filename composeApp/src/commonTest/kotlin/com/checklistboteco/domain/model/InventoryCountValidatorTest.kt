package com.checklistboteco.domain.model

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class InventoryCountValidatorTest {
    @Test fun acceptsValidBeverage(){ assertTrue(InventoryCountValidator.validate(InventoryCountDraft(name="Heineken",quantity=24.0,category=InventoryCategory.ALCOOLICO,volume=600.0,volumeUnit="ML",salePriceInCents=1800,costPriceInCents=900,storageCondition=StorageCondition.GELADO)).isEmpty()) }
    @Test fun rejectsInvalidQuantityVolumeAndUnit(){ val errors=InventoryCountValidator.validate(InventoryCountDraft(name="",quantity=-1.0,category=InventoryCategory.NAO_ALCOOLICO,volume=0.0,volumeUnit="L",salePriceInCents=-1,storageCondition=StorageCondition.NATURAL)); assertEquals(5,errors.size) }
}
