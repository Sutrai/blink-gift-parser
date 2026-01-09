package com.ceawse.coreprocessor.service.impl;

import com.ceawse.coreprocessor.model.CurrentSaleDocument;
import com.ceawse.coreprocessor.model.GiftHistoryDocument;
import com.ceawse.coreprocessor.model.MarketEventType;
import com.ceawse.coreprocessor.repository.CurrentSaleRepository;
import com.ceawse.coreprocessor.repository.SoldGiftRepository;
import com.ceawse.coreprocessor.service.MarketEventMapper;
import com.ceawse.coreprocessor.service.MarketProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketProcessorImpl implements MarketProcessor {

    private final CurrentSaleRepository currentSaleRepository;
    private final SoldGiftRepository soldGiftRepository;
    private final MongoTemplate mongoTemplate;
    private final MarketEventMapper mapper;

    @Override
    @Transactional
    public void processEvent(GiftHistoryDocument event) {
        MarketEventType type = MarketEventType.fromString(event.getEventType());
        log.debug("Processing event type: {} for address: {}", type, event.getAddress());

        switch (type) {
            case PUTUPFORSALE -> handleList(event);
            case SNAPSHOT_LIST -> handleSnapshotList(event);
            case CANCELSALE -> handleUnlist(event);
            case SOLD -> handleSold(event);
            case SNAPSHOT_FINISH -> handleSnapshotFinish(event);
            default -> log.debug("Event type {} ignored", type);
        }
    }

    private void handleList(GiftHistoryDocument event) {
        currentSaleRepository.findByAddress(event.getAddress())
                .ifPresentOrElse(
                        sale -> {
                            mapper.updateCurrentSale(sale, event);
                            currentSaleRepository.save(sale);
                        },
                        () -> currentSaleRepository.save(mapper.toCurrentSale(event))
                );
    }

    private void handleSnapshotList(GiftHistoryDocument event) {
        // Логика идентична handleList, но в будущем может отличаться, поэтому методы разделены
        handleList(event);
    }

    private void handleUnlist(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
    }

    private void handleSold(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
        soldGiftRepository.save(mapper.toSoldGift(event));
    }

    private void handleSnapshotFinish(GiftHistoryDocument event) {
        String currentSnapshotId = event.getSnapshotId();
        String marketplace = event.getMarketplace();
        long snapshotStartTime;

        try {
            String payload = event.getEventPayload() != null ? event.getEventPayload() : event.getPriceNano();
            snapshotStartTime = Long.parseLong(payload);
        } catch (NumberFormatException e) {
            log.error("Invalid snapshot start time payload: {}", event.getEventPayload());
            return;
        }

        Instant safeThreshold = Instant.ofEpochMilli(snapshotStartTime);
        log.info("Finalizing snapshot ID: {} for marketplace: {}. Threshold: {}", currentSnapshotId, marketplace, safeThreshold);

        Query query = new Query();
        if (marketplace != null) {
            query.addCriteria(Criteria.where("marketplace").is(marketplace));
        }

        // Удаляем записи, которые не были обновлены в текущем снапшоте и старее начала снапшота
        query.addCriteria(Criteria.where("lastSnapshotId").ne(currentSnapshotId));
        query.addCriteria(Criteria.where("updatedAt").lt(safeThreshold));

        var result = mongoTemplate.remove(query, CurrentSaleDocument.class);
        log.info("Snapshot finalized. Removed {} stale listings.", result.getDeletedCount());
    }
}