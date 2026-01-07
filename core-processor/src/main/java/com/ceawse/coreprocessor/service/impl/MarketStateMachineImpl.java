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

    // --- ЛОГИКА СНАПШОТА ---

    private void handleSnapshotList(GiftHistoryDocument event) {
        Long priceNano = parseNano(event.getPriceNano());

        // 1. Ищем существующую запись или создаем заготовку
        CurrentSaleDocument sale = currentSaleRepository.findByAddress(event.getAddress())
                .orElse(CurrentSaleDocument.builder()
                        .address(event.getAddress())
                        .collectionAddress(event.getCollectionAddress())
                        // Если записи не было, считаем временем листинга время снапшота (или можно null)
                        .listedAt(Instant.ofEpochMilli(event.getTimestamp()))
                        .build());

        // 2. Обновляем данные на актуальные из API
        sale.setName(event.getName());
        sale.setPrice(event.getPrice());
        sale.setPriceNano(priceNano);
        sale.setCurrency(event.getCurrency());
        sale.setSeller(event.getOldOwner()); // oldOwner в snapshot_list = продавец
        sale.setIsOffchain(Boolean.TRUE.equals(event.getIsOffchain()));

        // 3. ОБНОВЛЯЕМ МЕТКУ СНАПШОТА
        sale.setLastSnapshotId(event.getSnapshotId());
        sale.setUpdatedAt(Instant.now());

        currentSaleRepository.save(sale);
    }

    private void handleSnapshotFinish(GiftHistoryDocument event) {
        String currentSnapshotId = event.getSnapshotId();
        log.info("Finalizing snapshot ID: {}", currentSnapshotId);

        // 1. Формируем запрос на удаление
        // Удаляем всё, где lastSnapshotId НЕ равен текущему ID снапшота.
        // Это значит, парсер прошел по всей витрине и НЕ встретил эти товары.
        Query query = new Query();
        query.addCriteria(Criteria.where("lastSnapshotId").ne(currentSnapshotId));

        // В будущем здесь нужен будет addCriteria(Criteria.where("marketplace").is("getgems"));

        // 2. Выполняем удаление
        var result = mongoTemplate.remove(query, CurrentSaleDocument.class);

        log.info("Snapshot finalized. Removed {} stale listings.", result.getDeletedCount());
    }

    // --- ОБЫЧНАЯ ЛОГИКА (LIVE) ---

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
        sale.setUpdatedAt(Instant.ofEpochMilli(event.getTimestamp()));
        currentSaleRepository.save(sale);
    }

    private void handleUnlist(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
    }

    private void handleSold(GiftHistoryDocument event) {
        currentSaleRepository.deleteByAddress(event.getAddress());
        Long priceNano = parseNano(event.getPriceNano());
        SoldGiftDocument sold = SoldGiftDocument.builder()
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