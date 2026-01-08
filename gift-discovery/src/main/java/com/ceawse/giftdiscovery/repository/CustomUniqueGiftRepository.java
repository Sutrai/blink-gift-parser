package com.ceawse.giftdiscovery.repository;


import com.ceawse.giftdiscovery.model.GiftHistoryDocument;
import com.ceawse.giftdiscovery.model.ItemRegistryDocument;

import java.util.List;

public interface CustomUniqueGiftRepository {
    // Метод для вставки данных из Registry (Ончейн)
    void bulkUpsertFromRegistry(List<ItemRegistryDocument> items);

    // Метод для вставки данных из History (События)
    void bulkUpsertFromHistory(List<GiftHistoryDocument> events);
}