package com.ceawse.portalsparser.service.impl;

import com.ceawse.portalsparser.client.DiscoveryInternalClient;
import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.domain.PortalsIngestionState;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsNftDto;
import com.ceawse.portalsparser.dto.PortalsSearchResponseDto;
import com.ceawse.portalsparser.mapper.PortalsMapper;
import com.ceawse.portalsparser.repository.PortalsGiftHistoryRepository;
import com.ceawse.portalsparser.repository.PortalsIngestionStateRepository;
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
                // 1. Тянем страницу данных из API Portals
                PortalsSearchResponseDto response = apiClient.searchNfts(
                        offset, limit, "price asc", "listed", true
                );

                if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                    break;
                }

                List<PortalsNftDto> items = response.getResults();

                // 2. СОХРАНЯЕМ ИСТОРИЮ (это важно для графиков цен и аналитики)
                // Это остается здесь, так как это специфичные данные маркетплейса
                List<PortalsGiftHistoryDocument> events = items.stream()
                        .map(nft -> mapper.mapSnapshotToHistory(nft, snapshotId))
                        .toList();
                historyRepository.saveAll(events);

                // 3. ОТПРАВЛЯЕМ ДАННЫЕ В GIFT-DISCOVERY
                // Вместо того чтобы сохранять UniqueGiftDocument здесь, мы просим другой модуль сделать это
                items.forEach(this::sendToDiscovery);

                log.info("Portals Snapshot: processed {} items, offset {}", items.size(), offset);
                offset += limit;

                // Небольшая задержка, чтобы не спамить API и Discovery
                Thread.sleep(100);
            }

            // Фиксируем окончание снапшота в истории
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

                // Сохраняем событие (продажа/листинг) в историю
                PortalsGiftHistoryDocument doc = mapper.mapActionToHistory(action, actionTime);
                if (doc.getHash() != null && !historyRepository.existsByHash(doc.getHash())) {
                    historyRepository.save(doc);
                    savedCount++;
                }

                // Если в событии есть данные о подарке — шлем в Discovery
                if (action.getNft() != null) {
                    sendToDiscovery(action.getNft());
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

    /**
     * Тот самый метод, который заменяет сохранение в локальную БД.
     * Он просто берет сырые данные и кидает их в gift-discovery.
     */
    private void sendToDiscovery(PortalsNftDto nft) {
        try {
            String model = null, backdrop = null, symbol = null;
            if (nft.getAttributes() != null) {
                for (var attr : nft.getAttributes()) {
                    if ("model".equalsIgnoreCase(attr.getType())) model = attr.getValue();
                    if ("backdrop".equalsIgnoreCase(attr.getType())) backdrop = attr.getValue();
                    if ("symbol".equalsIgnoreCase(attr.getType())) symbol = attr.getValue();
                }
            }

            String fullName = nft.getName();
            if (nft.getExternalCollectionNumber() != null) {
                fullName += " #" + nft.getExternalCollectionNumber();
            }

            discoveryClient.enrich(DiscoveryInternalClient.EnrichmentRequest.builder()
                    .id(nft.getId()) // Адрес
                    .giftName(fullName)
                    .collectionAddress(nft.getCollectionId())
                    .model(model)
                    .backdrop(backdrop)
                    .symbol(symbol)
                    .timestamp(System.currentTimeMillis())
                    .build());

        } catch (Exception e) {
            log.warn("Failed to delegate discovery for {}: {}", nft.getName(), e.getMessage());
        }
    }

    private long parseTime(String isoTime) {
        try { return Instant.parse(isoTime).toEpochMilli(); } catch (Exception e) { return System.currentTimeMillis(); }
    }
}