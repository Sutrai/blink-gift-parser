package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.model.GiftHistoryDocument;
import com.ceawse.giftdiscovery.model.ItemRegistryDocument;
import com.ceawse.giftdiscovery.model.ProcessorState;
import com.ceawse.giftdiscovery.repository.ProcessorStateRepository;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import com.ceawse.giftdiscovery.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryServiceImpl implements DiscoveryService {

    private final UniqueGiftRepository uniqueGiftRepository;
    private final ProcessorStateRepository stateRepository;
    private final MongoTemplate mongoTemplate;

    private static final int BATCH_SIZE = 2000;
    private static final String ID_REGISTRY = "DISCOVERY_REGISTRY";
    private static final String ID_HISTORY = "DISCOVERY_HISTORY";

    @Override
    public void processRegistryStream() {
        ProcessorState state = getState(ID_REGISTRY);
        Instant lastTime = Instant.ofEpochMilli(state.getLastProcessedTimestamp());

        Query query = new Query(Criteria.where("lastSeenAt").gt(lastTime))
                .with(Sort.by(Sort.Direction.ASC, "lastSeenAt"))
                .limit(BATCH_SIZE);

        List<ItemRegistryDocument> items = mongoTemplate.find(query, ItemRegistryDocument.class);
        if (items.isEmpty()) return;

        log.info("Registry Stream: Processing {} items...", items.size());
        uniqueGiftRepository.bulkUpsertFromRegistry(items);

        long maxTime = items.stream()
                .mapToLong(i -> i.getLastSeenAt().toEpochMilli())
                .max()
                .orElse(state.getLastProcessedTimestamp());

        saveState(state, maxTime);
    }

    @Override
    public void processHistoryStream() {
        ProcessorState state = getState(ID_HISTORY);

        Query query = new Query(Criteria.where("timestamp").gt(state.getLastProcessedTimestamp()))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"))
                .limit(BATCH_SIZE);

        List<GiftHistoryDocument> events = mongoTemplate.find(query, GiftHistoryDocument.class);
        if (events.isEmpty()) return;

        log.info("History Stream: Processing {} events...", events.size());

        // Оптимизированная дедупликация
        List<GiftHistoryDocument> uniqueEvents = deduplicateEvents(events);

        uniqueGiftRepository.bulkUpsertFromHistory(uniqueEvents);

        long maxTime = events.getLast().getTimestamp();
        saveState(state, maxTime);
    }

    private List<GiftHistoryDocument> deduplicateEvents(List<GiftHistoryDocument> events) {
        // Оставляем только последнее событие для каждого адреса
        Map<String, GiftHistoryDocument> map = new HashMap<>();
        for (GiftHistoryDocument ev : events) {
            if (ev.getAddress() != null) {
                map.merge(ev.getAddress(), ev,
                        (oldV, newV) -> newV.getTimestamp() > oldV.getTimestamp() ? newV : oldV);
            }
        }
        return List.copyOf(map.values());
    }

    private ProcessorState getState(String id) {
        return stateRepository.findById(id).orElse(new ProcessorState(id, 0L, null));
    }

    private void saveState(ProcessorState state, long timestamp) {
        state.setLastProcessedTimestamp(timestamp);
        stateRepository.save(state);
    }
}