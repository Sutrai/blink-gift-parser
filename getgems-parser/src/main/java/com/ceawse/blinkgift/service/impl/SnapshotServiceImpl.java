package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.domain.UniqueGiftDocument;
import com.ceawse.blinkgift.dto.GetGemsSaleItemDto;
import com.ceawse.blinkgift.repository.GetGemsUniqueGiftRepository;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotServiceImpl {

    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final GiftHistoryRepository historyRepository;
    private final GetGemsUniqueGiftRepository uniqueGiftRepository; // Внедряем новый репозиторий

    @Async
    public void runSnapshot(String marketplace) {
        String snapshotId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        log.info("Starting SNAPSHOT id={} for {}", snapshotId, marketplace);

        try {
            var collections = indexerClient.getCollections();
            log.info("Collections to scan: {}", collections.size());
            for (var col : collections) {
                processCollection(col.address, snapshotId, marketplace);
            }
            finishSnapshot(snapshotId, startTime, marketplace);
        } catch (Exception e) {
            log.error("Snapshot FAILED", e);
        }
    }

    private void processCollection(String collectionAddress, String snapshotId, String marketplace) {
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                var response = getGemsClient.getOnSale(collectionAddress, 100, cursor);
                if (response == null || !response.isSuccess() || response.getResponse().getItems() == null || response.getResponse().getItems().isEmpty()) {
                    hasMore = false;
                    continue;
                }

                List<GetGemsSaleItemDto> items = response.getResponse().getItems();

                // 1. Сохраняем историю листингов (как было раньше)
                List<GiftHistoryDocument> events = items.stream()
                        .filter(i -> i.getSale() != null)
                        .map(i -> mapToEvent(i, snapshotId, marketplace))
                        .toList();

                if (!events.isEmpty()) {
                    historyRepository.saveAll(events);
                }

                // 2. НОВОЕ: Сохраняем уникальные подарки с атрибутами
                items.forEach(this::saveUniqueGiftWithAttributes);

                cursor = response.getResponse().getCursor();
                if (cursor == null) hasMore = false;
                Thread.sleep(150);

            } catch (Exception e) {
                log.error("Error processing collection {}: {}", collectionAddress, e.getMessage());
                hasMore = false;
            }
        }
    }

    private void saveUniqueGiftWithAttributes(GetGemsSaleItemDto item) {
        // Если атрибутов нет, пропускаем (чтобы не затирать возможно существующие данные пустыми)
        if (item.getAttributes() == null || item.getAttributes().isEmpty()) return;

        UniqueGiftDocument.GiftAttributes.GiftAttributesBuilder attrsBuilder = UniqueGiftDocument.GiftAttributes.builder();
        attrsBuilder.updatedAt(Instant.now());

        for (GetGemsSaleItemDto.AttributeDto attr : item.getAttributes()) {
            if ("Model".equalsIgnoreCase(attr.getTraitType())) attrsBuilder.model(attr.getValue());
            if ("Backdrop".equalsIgnoreCase(attr.getTraitType())) attrsBuilder.backdrop(attr.getValue());
            if ("Symbol".equalsIgnoreCase(attr.getTraitType())) attrsBuilder.symbol(attr.getValue());
        }

        UniqueGiftDocument doc = UniqueGiftDocument.builder()
                .id(item.getAddress()) // Адрес подарка
                .name(item.getName())
                .collectionAddress(item.getCollectionAddress())
                .isOffchain(item.isOffchain())
                .attributes(attrsBuilder.build())
                .lastSeenAt(Instant.now())
                .build();

        // Save работает как upsert: если документ с таким ID есть, он обновится
        uniqueGiftRepository.save(doc);
    }

    private GiftHistoryDocument mapToEvent(GetGemsSaleItemDto item, String snapshotId, String marketplace) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace(marketplace != null ? marketplace : "getgems");
        doc.setEventType("SNAPSHOT_LIST");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setAddress(item.getAddress());
        doc.setCollectionAddress(item.getCollectionAddress());
        doc.setName(item.getName());
        doc.setIsOffchain(item.isOffchain());
        doc.setHash(snapshotId + "_" + item.getAddress());

        if (item.getSale() != null) {
            String nano = item.getSale().getFullPrice();
            doc.setPriceNano(nano);
            doc.setPrice(fromNano(nano));
            doc.setCurrency(item.getSale().getCurrency());
            doc.setOldOwner(item.getOwnerAddress());
        }
        return doc;
    }

    private void finishSnapshot(String snapshotId, long startTime, String marketplace) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace(marketplace != null ? marketplace : "getgems");
        doc.setEventType("SNAPSHOT_FINISH");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setEventPayload(String.valueOf(startTime));
        doc.setPriceNano("0");
        doc.setHash("FINISH_" + snapshotId);
        doc.setAddress("SYSTEM");
        doc.setCollectionAddress("SYSTEM");

        historyRepository.save(doc);
        log.info("Snapshot {} FINISHED.", snapshotId);
    }

    private String fromNano(String nano) {
        if (nano == null) return "0";
        try {
            BigDecimal val = new BigDecimal(nano);
            return val.divide(BigDecimal.valueOf(1_000_000_000)).toPlainString();
        } catch (Exception e) {
            return "0";
        }
    }
}