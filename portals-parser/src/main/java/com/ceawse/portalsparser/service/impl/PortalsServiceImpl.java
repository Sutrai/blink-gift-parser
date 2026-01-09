package com.ceawse.portalsparser.service.impl;

import com.ceawse.portalsparser.client.DiscoveryInternalClient;
import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.domain.PortalsIngestionState;
import com.ceawse.portalsparser.domain.UniqueGiftDocument;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsNftDto;
import com.ceawse.portalsparser.dto.PortalsSearchResponseDto;
import com.ceawse.portalsparser.mapper.PortalsMapper;
import com.ceawse.portalsparser.repository.PortalsGiftHistoryRepository;
import com.ceawse.portalsparser.repository.PortalsIngestionStateRepository;
import com.ceawse.portalsparser.repository.PortalsUniqueGiftRepository;
import com.ceawse.portalsparser.service.PortalsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalsServiceImpl implements PortalsService {

    private final PortalsApiClient apiClient;
    private final PortalsGiftHistoryRepository historyRepository;
    private final PortalsUniqueGiftRepository uniqueGiftRepository;
    private final PortalsIngestionStateRepository stateRepository;
    private final PortalsMapper mapper;
    private final DiscoveryInternalClient discoveryClient;

    private static final String REALTIME_PROCESS_ID = "PORTALS_LIVE";

    @Override
    @Async("taskExecutor")
    public void runSnapshot() {
        String snapshotId = UUID.randomUUID().toString();
        log.info("Starting Portals SNAPSHOT id={}", snapshotId);
        long startTime = System.currentTimeMillis();
        int offset = 0;
        int limit = 50;

        try {
            while (true) {
                PortalsSearchResponseDto response = apiClient.searchNfts(
                        offset, limit, "price asc", "listed", true
                );

                if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                    break;
                }

                List<PortalsNftDto> items = response.getResults();

                List<PortalsGiftHistoryDocument> events = items.stream()
                        .map(nft -> mapper.mapSnapshotToHistory(nft, snapshotId))
                        .toList();
                historyRepository.saveAll(events);

                // ОБОГАЩАЕМ КАЖДЫЙ NFT ПЕРЕД СОХРАНЕНИЕМ
                items.stream()
                        .filter(nft -> nft.getAttributes() != null && !nft.getAttributes().isEmpty())
                        .forEach(this::processNft);

                log.info("Portals Snapshot: processed {} items, offset {}", items.size(), offset);
                offset += limit;
                Thread.sleep(200);
            }

            historyRepository.save(mapper.createSnapshotFinishEvent(snapshotId, startTime));
            log.info("Portals Snapshot {} FINISHED.", snapshotId);

        } catch (Exception e) {
            log.error("Portals Snapshot failed", e);
        }
    }

    @Override
    public void processRealtimeEvents() {
        try {
            PortalsIngestionState state = stateRepository.findById(REALTIME_PROCESS_ID)
                    .orElseGet(() -> new PortalsIngestionState(REALTIME_PROCESS_ID, System.currentTimeMillis() - 60000));

            PortalsActionsResponseDto response = apiClient.getMarketActivity(0, 50, "listed_at desc", "buy,listing,price_update");

            if (response == null || response.getActions() == null || response.getActions().isEmpty()) return;

            long lastProcessedTime = state.getLastProcessedTimestamp();
            long newMaxTime = lastProcessedTime;
            int savedCount = 0;

            for (PortalsActionsResponseDto.ActionDto action : response.getActions()) {
                long actionTime = parseTime(action.getCreatedAt());
                if (actionTime <= lastProcessedTime) continue;

                PortalsGiftHistoryDocument doc = mapper.mapActionToHistory(action, actionTime);
                if (doc.getHash() != null && !historyRepository.existsByHash(doc.getHash())) {
                    historyRepository.save(doc);
                    savedCount++;
                }

                if (action.getNft() != null && action.getNft().getAttributes() != null) {
                    processNft(action.getNft()); // ОБОГАЩАЕМ
                }

                if (actionTime > newMaxTime) newMaxTime = actionTime;
            }

            if (savedCount > 0) {
                state.setLastProcessedTimestamp(newMaxTime);
                stateRepository.save(state);
            }
        } catch (Exception e) {
            log.error("Portals Realtime Error: {}", e.getMessage());
        }
    }

    private void processNft(PortalsNftDto nft) {
        UniqueGiftDocument uniqueGift = mapper.mapToUniqueGift(nft);
        try {
            var attr = uniqueGift.getAttributes();
            var enrichment = discoveryClient.enrich(DiscoveryInternalClient.EnrichmentRequest.builder()
                    .giftName(uniqueGift.getName())
                    .collectionAddress(uniqueGift.getCollectionAddress())
                    .model(attr.getModel())
                    .backdrop(attr.getBackdrop())
                    .symbol(attr.getSymbol())
                    .build());

            uniqueGift.setCollectionAddress(enrichment.getResolvedCollectionAddress());

            attr.setModelPrice(enrichment.getModelPrice());
            attr.setModelRarityCount(enrichment.getModelCount());
            attr.setBackdropPrice(enrichment.getBackdropPrice());
            attr.setBackdropRarityCount(enrichment.getBackdropCount());
            attr.setSymbolPrice(enrichment.getSymbolPrice());
            attr.setSymbolRarityCount(enrichment.getSymbolCount());

            uniqueGift.setMarketData(UniqueGiftDocument.MarketData.builder()
                    .collectionFloorPrice(enrichment.getCollectionFloorPrice())
                    .estimatedPrice(enrichment.getEstimatedPrice())
                    .priceUpdatedAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.warn("Enrichment failed for {}: {}", uniqueGift.getName(), e.getMessage());
        }
        uniqueGiftRepository.save(uniqueGift);
    }

    private long parseTime(String isoTime) {
        try { return Instant.parse(isoTime).toEpochMilli(); } catch (Exception e) { return System.currentTimeMillis(); }
    }
}