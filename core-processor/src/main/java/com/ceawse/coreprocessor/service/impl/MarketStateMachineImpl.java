package com.ceawse.coreprocessor.service.impl;

import com.ceawse.coreprocessor.model.CurrentSaleDocument;
import com.ceawse.coreprocessor.model.GiftHistoryDocument;
import com.ceawse.coreprocessor.model.SoldGiftDocument;
import com.ceawse.coreprocessor.repository.CurrentSaleRepository;
import com.ceawse.coreprocessor.repository.SoldGiftRepository;
import com.ceawse.coreprocessor.service.MarketStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketStateMachineImpl implements MarketStateMachine {

    private final CurrentSaleRepository currentSaleRepository;
    private final SoldGiftRepository soldGiftRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    @Transactional
    public void applyEvent(GiftHistoryDocument event) {
        String type = event.getEventType() != null ? event.getEventType().toUpperCase() : "UNKNOWN";
        switch (type) {
            case "PUTUPFORSALE":
                handleList(event);
                break;
            case "CANCELSALE":
                handleUnlist(event);
                break;
            case "SOLD":
                handleSold(event);
                break;
            case "SNAPSHOT_LIST":
                handleSnapshotList(event);
                break;
            case "SNAPSHOT_FINISH":
                handleSnapshotFinish(event);
                break;
            default:
                break;
        }
    }

    private void handleSnapshotList(GiftHistoryDocument event) {
        Long priceNano = parseNano(event.getPriceNano());
        CurrentSaleDocument sale = currentSaleRepository.findByAddress(event.getAddress())
                .orElse(CurrentSaleDocument.builder()
                        .address(event.getAddress())
                        .collectionAddress(event.getCollectionAddress())
                        .listedAt(Instant.ofEpochMilli(event.getTimestamp()))
                        .build());

        sale.setName(event.getName());
        sale.setPrice(event.getPrice());
        sale.setPriceNano(priceNano);
        sale.setCurrency(event.getCurrency());
        sale.setSeller(event.getOldOwner());
        sale.setOffchain(Boolean.TRUE.equals(event.getIsOffchain()));
        sale.setLastSnapshotId(event.getSnapshotId());

        // Исправление #2: System Time
        sale.setUpdatedAt(Instant.now());

        currentSaleRepository.save(sale);
    }

    private void handleSnapshotFinish(GiftHistoryDocument event) {
        String currentSnapshotId = event.getSnapshotId();

        long snapshotStartTime = 0L;
        try {
            if (event.getEventPayload() != null) {
                snapshotStartTime = Long.parseLong(event.getEventPayload());
            } else {
                snapshotStartTime = Long.parseLong(event.getPriceNano());
            }
        } catch (NumberFormatException e) {
            log.error("Invalid snapshot start time payload: {}", event.getEventPayload());
            return;
        }

        Instant safeThreshold = Instant.ofEpochMilli(snapshotStartTime);
        log.info("Finalizing snapshot ID: {}. Threshold: {}", currentSnapshotId, safeThreshold);

        Query query = new Query();
        query.addCriteria(Criteria.where("lastSnapshotId").ne(currentSnapshotId));
        query.addCriteria(Criteria.where("updatedAt").lt(safeThreshold));

        var result = mongoTemplate.remove(query, CurrentSaleDocument.class);
        log.info("Snapshot finalized. Removed {} stale listings.", result.getDeletedCount());
    }

    private void handleList(GiftHistoryDocument event) {
        Long priceNano = parseNano(event.getPriceNano());
        CurrentSaleDocument sale = currentSaleRepository.findByAddress(event.getAddress())
                .orElse(CurrentSaleDocument.builder()
                        .address(event.getAddress())
                        .collectionAddress(event.getCollectionAddress())
                        .name(event.getName())
                        .isOffchain(Boolean.TRUE.equals(event.getIsOffchain()))
                        .listedAt(Instant.ofEpochMilli(event.getTimestamp()))
                        .build());

        sale.setPrice(event.getPrice());
        sale.setPriceNano(priceNano);
        sale.setCurrency(event.getCurrency());
        sale.setSeller(event.getOldOwner());
        sale.setUpdatedAt(Instant.now());

        currentSaleRepository.save(sale);
    }

    private void handleUnlist(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
    }

    private void handleSold(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());

        Long priceNano = parseNano(event.getPriceNano());

        String deterministicId = event.getHash();

        SoldGiftDocument sold = SoldGiftDocument.builder()
                .id(deterministicId)
                .address(event.getAddress())
                .collectionAddress(event.getCollectionAddress())
                .name(event.getName())
                .price(event.getPrice())
                .priceNano(priceNano)
                .currency(event.getCurrency())
                .seller(event.getOldOwner())
                .buyer(event.getNewOwner())
                .soldAt(Instant.ofEpochMilli(event.getTimestamp()))
                .isOffchain(Boolean.TRUE.equals(event.getIsOffchain()))
                .build();

        soldGiftRepository.save(sold);
    }

    private Long parseNano(String nanoStr) {
        if (nanoStr == null) return 0L;
        try {
            return Long.parseLong(nanoStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}