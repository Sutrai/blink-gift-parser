package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.domain.UniqueGiftDocument;
import com.ceawse.blinkgift.dto.GetGemsSaleItemDto;
import com.ceawse.blinkgift.mapper.EventMapper;
import com.ceawse.blinkgift.repository.GetGemsUniqueGiftRepository;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import com.ceawse.blinkgift.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotServiceImpl implements SnapshotService {

    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final GiftHistoryRepository historyRepository;
    private final GetGemsUniqueGiftRepository uniqueGiftRepository;
    private final EventMapper eventMapper;

    @Async
    @Override
    public void runSnapshot(String marketplace) {
        String snapshotId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        log.info("Starting SNAPSHOT id={} for {} (Optimized for 15 Proxies)", snapshotId, marketplace);

        try {
            var collections = indexerClient.getCollections();
            log.info("Found {} collections to scan", collections.size());

            for (var col : collections) {
                processCollection(col.address, snapshotId);
            }

            finishSnapshot(snapshotId, startTime, marketplace);
        } catch (Exception e) {
            log.error("Snapshot FAILED", e);
        }
    }

    private void processCollection(String collectionAddress, String snapshotId) {
        String cursor = null;
        boolean hasMore = true;
        // Берем по 100 элементов (максимум API), чтобы минимизировать кол-во запросов
        int batchSize = 100;

        while (hasMore) {
            try {
                var response = getGemsClient.getOnSale(collectionAddress, batchSize, cursor);
                if (response == null || !response.isSuccess() ||
                        response.getResponse().getItems() == null ||
                        response.getResponse().getItems().isEmpty()) {
                    hasMore = false;
                    continue;
                }

                List<GetGemsSaleItemDto> items = response.getResponse().getItems();

                List<GiftHistoryDocument> historyDocs = items.stream()
                        .filter(i -> i.getSale() != null)
                        .map(i -> eventMapper.toSnapshotEntity(i, snapshotId))
                        .toList();

                List<UniqueGiftDocument> uniqueDocs = items.stream()
                        .filter(i -> i.getAttributes() != null && !i.getAttributes().isEmpty())
                        .map(eventMapper::toUniqueGiftEntity)
                        .toList();

                if (!historyDocs.isEmpty()) historyRepository.saveAll(historyDocs);
                if (!uniqueDocs.isEmpty()) uniqueGiftRepository.saveAll(uniqueDocs);

                cursor = response.getResponse().getCursor();
                if (cursor == null) hasMore = false;

                Thread.sleep(20);

            } catch (Exception e) {
                log.error("Error processing collection {}: {}", collectionAddress, e.getMessage());
                hasMore = false;
            }
        }
    }

    private void finishSnapshot(String snapshotId, long startTime, String marketplace) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace(marketplace);
        doc.setEventType("SNAPSHOT_FINISH");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setEventPayload(String.valueOf(startTime));
        doc.setPriceNano("0");
        doc.setHash("FINISH_" + snapshotId);
        doc.setAddress("SYSTEM");
        doc.setCollectionAddress("SYSTEM");

        historyRepository.save(doc);
        log.info("Snapshot {} FINISHED. Duration: {}ms", snapshotId, System.currentTimeMillis() - startTime);
    }
}