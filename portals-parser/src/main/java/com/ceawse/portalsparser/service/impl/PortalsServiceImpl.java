package com.ceawse.portalsparser.service.impl;

import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.domain.PortalsIngestionState;
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

                items.stream()
                        .filter(nft -> nft.getAttributes() != null && !nft.getAttributes().isEmpty())
                        .map(mapper::mapToUniqueGift)
                        .forEach(uniqueGiftRepository::save);

                log.info("Portals Snapshot: saved {} items, offset {}", events.size(), offset);
                offset += limit;

                Thread.sleep(200);
            }

            historyRepository.save(mapper.createSnapshotFinishEvent(snapshotId, startTime));
            log.info("Portals Snapshot {} FINISHED. Duration: {}ms", snapshotId, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Portals Snapshot failed", e);
        }
    }

    @Override
    public void processRealtimeEvents() {
        try {
            PortalsIngestionState state = stateRepository.findById(REALTIME_PROCESS_ID)
                    .orElseGet(() -> new PortalsIngestionState(REALTIME_PROCESS_ID, System.currentTimeMillis() - 60000));

            PortalsActionsResponseDto response = apiClient.getMarketActivity(
                    0, 50, "listed_at desc", "buy,listing,price_update"
            );

            if (response == null || response.getActions() == null || response.getActions().isEmpty()) {
                return;
            }

            long lastProcessedTime = state.getLastProcessedTimestamp();
            long newMaxTime = lastProcessedTime;
            int savedCount = 0;

            for (PortalsActionsResponseDto.ActionDto action : response.getActions()) {
                long actionTime = parseTime(action.getCreatedAt());

                if (actionTime <= lastProcessedTime) {
                    continue;
                }

                PortalsGiftHistoryDocument doc = mapper.mapActionToHistory(action, actionTime);
                if (doc.getHash() != null && !historyRepository.existsByHash(doc.getHash())) {
                    historyRepository.save(doc);
                    savedCount++;
                }

                if (action.getNft() != null && action.getNft().getAttributes() != null) {
                    uniqueGiftRepository.save(mapper.mapToUniqueGift(action.getNft()));
                }

                if (actionTime > newMaxTime) {
                    newMaxTime = actionTime;
                }
            }

            if (savedCount > 0) {
                log.info("Portals Realtime: saved {} new events.", savedCount);
                state.setLastProcessedTimestamp(newMaxTime);
                stateRepository.save(state);
            }

        } catch (Exception e) {
            log.error("Error polling Portals API: {}", e.getMessage());
        }
    }

    private long parseTime(String isoTime) {
        try {
            return Instant.parse(isoTime).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}