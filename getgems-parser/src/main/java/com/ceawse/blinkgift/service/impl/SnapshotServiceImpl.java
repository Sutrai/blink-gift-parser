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

        long startTime = System.currentTimeMillis();

        log.info("Starting SNAPSHOT id={} for {}", snapshotId, marketplace);

        try {
            var collections = indexerClient.getCollections();
            log.info("Collections to scan: {}", collections.size());

            for (var col : collections) {
                processCollection(col.address, snapshotId);
            }

            finishSnapshot(snapshotId, startTime);
        } catch (Exception e) {
            log.error("Snapshot FAILED", e);
        }
    }

    private void processCollection(String collectionAddress, String snapshotId) {
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
                List<GiftHistoryDocument> events = items.stream()
                        .filter(i -> i.getSale() != null)
                        .map(i -> mapToEvent(i, snapshotId))
                        .toList();

                if (!events.isEmpty()) {
                    historyRepository.saveAll(events);
                }

                cursor = response.getResponse().getCursor();
                if (cursor == null) hasMore = false;

                Thread.sleep(150);
            } catch (Exception e) {
                log.error("Error processing collection {}: {}", collectionAddress, e.getMessage());
                hasMore = false;
            }
        }
    }

    private GiftHistoryDocument mapToEvent(GetGemsSaleItemDto item, String snapshotId) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace("getgems");
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

    private void finishSnapshot(String snapshotId, long startTime) {
        GiftHistoryDocument doc = new GiftHistoryDocument();
        doc.setMarketplace("getgems");
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