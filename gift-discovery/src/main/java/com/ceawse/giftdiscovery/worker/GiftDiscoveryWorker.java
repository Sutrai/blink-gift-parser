package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.model.GiftHistoryDocument;
import com.ceawse.giftdiscovery.model.ItemRegistryDocument;
import com.ceawse.giftdiscovery.model.ProcessorState;
import com.ceawse.giftdiscovery.repository.ProcessorStateRepository;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GiftDiscoveryWorker {

    private final UniqueGiftRepository uniqueGiftRepository;
    private final ProcessorStateRepository stateRepository;
    private final MongoTemplate mongoTemplate;

    private static final int BATCH_SIZE = 2000;
    private static final String ID_REGISTRY = "DISCOVERY_REGISTRY";
    private static final String ID_HISTORY = "DISCOVERY_HISTORY";

    // --- ПОТОК 1: REGISTRY (On-chain source) ---
    @Scheduled(fixedDelay = 5000)
    public void processRegistry() {
        ProcessorState state = getState(ID_REGISTRY);
        // Используем lastProcessedTimestamp как курсор для lastSeenAt (или mintedAt)
        // Для простоты привяжемся к lastSeenAt
        Instant lastTime = Instant.ofEpochMilli(state.getLastProcessedTimestamp());

        Query query = new Query();
        query.addCriteria(Criteria.where("lastSeenAt").gt(lastTime));
        query.with(Sort.by(Sort.Direction.ASC, "lastSeenAt"));
        query.limit(BATCH_SIZE);

        List<ItemRegistryDocument> items = mongoTemplate.find(query, ItemRegistryDocument.class);
        if (items.isEmpty()) return;

        log.info("Registry Stream: Processing {} items...", items.size());
        uniqueGiftRepository.bulkUpsertFromRegistry(items);

        long maxTime = items.stream()
                .mapToLong(i -> i.getLastSeenAt().toEpochMilli())
                .max().orElse(state.getLastProcessedTimestamp());

        saveState(state, maxTime);
    }

    // --- ПОТОК 2: HISTORY (Off-chain & updates) ---
    @Scheduled(fixedDelay = 2000)
    public void processHistory() {
        ProcessorState state = getState(ID_HISTORY);
        long lastTime = state.getLastProcessedTimestamp();

        Query query = new Query();
        query.addCriteria(Criteria.where("timestamp").gt(lastTime));
        query.with(Sort.by(Sort.Direction.ASC, "timestamp"));
        query.limit(BATCH_SIZE);

        List<GiftHistoryDocument> events = mongoTemplate.find(query, GiftHistoryDocument.class);
        if (events.isEmpty()) return;

        log.info("History Stream: Processing {} events...", events.size());

        // Дедупликация в памяти: оставляем только последнее событие для каждого адреса в этом батче
        Map<String, GiftHistoryDocument> uniqueBatch = new HashMap<>();
        for (GiftHistoryDocument ev : events) {
            if (ev.getAddress() == null) continue;
            uniqueBatch.merge(ev.getAddress(), ev, (oldV, newV) ->
                    newV.getTimestamp() > oldV.getTimestamp() ? newV : oldV
            );
        }

        uniqueGiftRepository.bulkUpsertFromHistory(uniqueBatch.values().stream().toList());

        long maxTime = events.get(events.size() - 1).getTimestamp();
        saveState(state, maxTime);
    }

    private ProcessorState getState(String id) {
        return stateRepository.findById(id)
                .orElse(new ProcessorState(id, 0L, null));
    }

    private void saveState(ProcessorState state, long timestamp) {
        state.setLastProcessedTimestamp(timestamp);
        stateRepository.save(state);
    }
}