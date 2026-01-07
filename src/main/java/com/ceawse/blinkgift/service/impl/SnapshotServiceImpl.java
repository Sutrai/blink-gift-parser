package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsSaleItemDto;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotServiceImpl {

    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final GiftHistoryRepository historyRepository;

    @Async
    public void runSnapshot(String marketplace) {
        String snapshotId = UUID.randomUUID().toString();
        log.info("Starting SNAPSHOT id={} for {}", snapshotId, marketplace);

        try {
            // 1. Берем коллекции из индексера
            var collections = indexerClient.getCollections();
            log.info("Collections to scan: {}", collections.size());

            // 2. Сканируем каждую коллекцию
            for (var col : collections) {
                processCollection(col.address, snapshotId);
            }

            // 3. Отправляем событие завершения
            finishSnapshot(snapshotId);

        } catch (Exception e) {
            log.error("Snapshot FAILED", e);
        }
    }

    private void processCollection(String collectionAddress, String snapshotId) {
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                // Запрос к GetGems
                var response = getGemsClient.getOnSale(collectionAddress, 100, cursor);

                if (response == null || !response.isSuccess() || response.getResponse().getItems() == null || response.getResponse().getItems().isEmpty()) {
                    hasMore = false;
                    continue;
                }

                List<GetGemsSaleItemDto> items = response.getResponse().getItems();

                // Маппим только те, у которых есть блок sale (на всякий случай)
                List<GiftHistoryDocument> events = items.stream()
                        .filter(i -> i.getSale() != null)
                        .map(i -> mapToEvent(i, snapshotId))
                        .toList();

                if (!events.isEmpty()) {
                    historyRepository.saveAll(events);
                }

                cursor = response.getResponse().getCursor();
                if (cursor == null) hasMore = false;

                // Небольшая задержка, чтобы не поймать 429
                Thread.sleep(150);

            } catch (Exception e) {
                log.error("Error processing collection {}: {}", collectionAddress, e.getMessage());
                hasMore = false; // При ошибке пропускаем коллекцию
            }
        }
    }

    private GiftHistoryDocument mapToEvent(GetGemsSaleItemDto item, String snapshotId) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setEventType("SNAPSHOT_LIST");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());

        doc.setAddress(item.getAddress());
        doc.setCollectionAddress(item.getCollectionAddress());
        doc.setName(item.getName());
        doc.setIsOffchain(item.isOffchain());

        // Hash = SnapshotID + Address. Гарантирует уникальность записи в рамках одного снапшота.
        doc.setHash(snapshotId + "_" + item.getAddress());

        if (item.getSale() != null) {
            String nano = item.getSale().getFullPrice();
            doc.setPriceNano(nano);
            doc.setPrice(fromNano(nano));
            doc.setCurrency(item.getSale().getCurrency());
            doc.setOldOwner(item.getOwnerAddress()); // Продавец
        }

        return doc;
    }

    private void finishSnapshot(String snapshotId) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setEventType("SNAPSHOT_FINISH");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setHash("FINISH_" + snapshotId);
        // Заглушки для обязательных полей
        doc.setAddress("SYSTEM");
        doc.setCollectionAddress("SYSTEM");

        historyRepository.save(doc);
        log.info("Snapshot {} FINISHED. Finish event saved.", snapshotId);
    }

    // Хелпер: Nano -> String (Human)
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