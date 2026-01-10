package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.DiscoveryInternalClient;
import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsSaleItemDto;
import com.ceawse.blinkgift.mapper.EventMapper;
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
    private final EventMapper eventMapper;
    private final DiscoveryInternalClient discoveryClient;

    @Async
    @Override
    public void runSnapshot(String marketplace) {
        String snapshotId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        log.info("Starting SNAPSHOT id={} for {}", snapshotId, marketplace);

        try {
            var collections = indexerClient.getCollections();
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

                // 1. Сохраняем в историю (SNAPSHOT_LIST)
                List<GiftHistoryDocument> historyDocs = items.stream()
                        .filter(i -> i.getSale() != null)
                        .map(i -> eventMapper.toSnapshotEntity(i, snapshotId))
                        .toList();
                if (!historyDocs.isEmpty()) historyRepository.saveAll(historyDocs);

                // 2. Отправляем в Discovery
                items.forEach(this::sendToDiscovery);

                cursor = response.getResponse().getCursor();
                if (cursor == null) hasMore = false;

                Thread.sleep(100);

            } catch (Exception e) {
                log.error("Error processing collection {}: {}", collectionAddress, e.getMessage());
                hasMore = false;
            }
        }
    }

    private void sendToDiscovery(GetGemsSaleItemDto item) {
        try {
            String model = null, backdrop = null, symbol = null;
            if (item.getAttributes() != null) {
                for (var attr : item.getAttributes()) {
                    if ("Model".equalsIgnoreCase(attr.getTraitType())) model = attr.getValue();
                    if ("Backdrop".equalsIgnoreCase(attr.getTraitType())) backdrop = attr.getValue();
                    if ("Symbol".equalsIgnoreCase(attr.getTraitType())) symbol = attr.getValue();
                }
            }

            discoveryClient.enrich(DiscoveryInternalClient.EnrichmentRequest.builder()
                    .id(item.getAddress())
                    .giftName(item.getName())
                    .collectionAddress(item.getCollectionAddress())
                    .model(model)
                    .backdrop(backdrop)
                    .symbol(symbol)
                    .timestamp(System.currentTimeMillis())
                    .build());

        } catch (Exception e) {
            log.warn("Snapshot delegation failed for {}: {}", item.getName(), e.getMessage());
        }
    }

    private void finishSnapshot(String snapshotId, long startTime, String marketplace) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace(marketplace);
        doc.setEventType("SNAPSHOT_FINISH");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setEventPayload(String.valueOf(startTime));
        doc.setHash("FINISH_" + snapshotId);
        doc.setAddress("SYSTEM");
        doc.setCollectionAddress("SYSTEM");

        historyRepository.save(doc);
        log.info("Snapshot {} FINISHED.", snapshotId);
    }
}