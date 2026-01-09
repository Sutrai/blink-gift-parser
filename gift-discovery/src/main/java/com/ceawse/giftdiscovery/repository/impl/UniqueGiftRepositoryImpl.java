package com.ceawse.giftdiscovery.repository.impl;

import com.ceawse.giftdiscovery.model.GiftHistoryDocument;
import com.ceawse.giftdiscovery.model.ItemRegistryDocument;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.repository.CustomUniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class UniqueGiftRepositoryImpl implements CustomUniqueGiftRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void bulkUpsertFromRegistry(List<ItemRegistryDocument> items) {
        if (items.isEmpty()) return;

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UniqueGiftDocument.class);

        for (ItemRegistryDocument item : items) {
            Query query = new Query(Criteria.where("_id").is(item.getAddress()));
            Update update = new Update()
                    .set("name", item.getName())
                    .set("collectionAddress", item.getCollectionAddress())
                    .set("isOffchain", false)
                    .set("lastSeenAt", item.getLastSeenAt())
                    .set("discoverySource", "REGISTRY")
                    .setOnInsert("firstSeenAt", item.getMintedAt());

            bulkOps.upsert(query, update);
        }
        bulkOps.execute();
    }

    @Override
    public void bulkUpsertFromHistory(List<GiftHistoryDocument> events) {
        if (events.isEmpty()) return;

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UniqueGiftDocument.class);

        for (GiftHistoryDocument event : events) {
            if (event.getAddress() == null) continue;

            Query query = new Query(Criteria.where("_id").is(event.getAddress()));
            Instant eventTime = Instant.ofEpochMilli(event.getTimestamp());

            Update update = new Update()
                    .setOnInsert("firstSeenAt", eventTime)
                    .setOnInsert("discoverySource", "HISTORY")
                    .set("lastSeenAt", eventTime);

            if (event.getName() != null) update.set("name", event.getName());
            if (event.getCollectionAddress() != null) update.set("collectionAddress", event.getCollectionAddress());

            // On-chain event always sets isOffchain=false.
            // Off-chain event only sets true if record doesn't exist.
            if (!Boolean.TRUE.equals(event.getIsOffchain())) {
                update.set("isOffchain", false);
            } else {
                update.setOnInsert("isOffchain", true);
            }

            bulkOps.upsert(query, update);
        }
        bulkOps.execute();
    }
}