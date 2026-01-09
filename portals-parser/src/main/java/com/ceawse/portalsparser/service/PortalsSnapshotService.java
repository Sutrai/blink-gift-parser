package com.ceawse.portalsparser.service;

import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.dto.PortalsSearchResponseDto;
import com.ceawse.portalsparser.dto.PortalsNftDto;
import com.ceawse.portalsparser.repository.PortalsGiftHistoryRepository;
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
public class PortalsSnapshotService {

    private final PortalsApiClient apiClient;
    private final PortalsGiftHistoryRepository historyRepository;

    @Async
    public void runSnapshot() {
        String snapshotId = UUID.randomUUID().toString();
        log.info("Starting Portals SNAPSHOT id={}", snapshotId);
        long startTime = System.currentTimeMillis();

        int offset = 0;
        int limit = 50;
        boolean hasMore = true;

        try {
            while (hasMore) {
                // sort_by=price+asc соответствует логике поиска ликвидности
                PortalsSearchResponseDto response = apiClient.searchNfts(
                        offset, limit, "price+asc", "listed", true
                );

                if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                    hasMore = false;
                    continue;
                }

                List<PortalsGiftHistoryDocument> events = response.getResults().stream()
                        .map(nft -> mapToSnapshotEvent(nft, snapshotId))
                        .toList();

                historyRepository.saveAll(events);
                log.info("Portals Snapshot: saved {} items, offset {}", events.size(), offset);

                offset += limit;
                // Задержка чтобы не спамить API
                Thread.sleep(200);
            }

            finishSnapshot(snapshotId, startTime);

        } catch (Exception e) {
            log.error("Portals Snapshot failed", e);
        }
    }

    private PortalsGiftHistoryDocument mapToSnapshotEvent(PortalsNftDto nft, String snapshotId) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace("portals");
        doc.setEventType("SNAPSHOT_LIST");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());

        doc.setAddress(nft.getId()); // В Portals используем UUID как адрес
        doc.setCollectionAddress(nft.getCollectionId());
        doc.setName(nft.getName());
        doc.setIsOffchain(true);

        // Уникальный хеш для снапшота
        doc.setHash(snapshotId + "_" + nft.getId());

        if (nft.getPrice() != null) {
            doc.setPrice(nft.getPrice());
            doc.setPriceNano(toNano(nft.getPrice()));
            doc.setCurrency("TON");
        }
        return doc;
    }

    private void finishSnapshot(String snapshotId, long startTime) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace("portals");
        doc.setEventType("SNAPSHOT_FINISH");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setEventPayload(String.valueOf(startTime));
        doc.setPriceNano("0");
        doc.setHash("PORTALS_FINISH_" + snapshotId);
        doc.setAddress("SYSTEM_PORTALS");
        doc.setCollectionAddress("SYSTEM_PORTALS");
        historyRepository.save(doc);
        log.info("Portals Snapshot {} FINISHED.", snapshotId);
    }

    private String toNano(String price) {
        try {
            return new BigDecimal(price).multiply(BigDecimal.valueOf(1_000_000_000)).toBigInteger().toString();
        } catch (Exception e) {
            return "0";
        }
    }
}