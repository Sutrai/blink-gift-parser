package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsHistoryDto;
import com.ceawse.blinkgift.mapper.EventMapper;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import com.ceawse.blinkgift.service.HistoryService;
import com.ceawse.blinkgift.service.StateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final GetGemsApiClient apiClient;
    private final GiftHistoryRepository repository;
    private final StateService stateService;
    private final EventMapper mapper;

    private static final String PROCESS_ID = "GETGEMS_LIVE";
    private static final List<String> TARGET_TYPES = List.of("sold", "cancelSale", "putUpForSale");

    @Override
    public void fetchRealtimeEvents() {
        try {
            long lastTime = stateService.getState(PROCESS_ID).getLastProcessedTimestamp();
            if (lastTime == 0) {
                lastTime = System.currentTimeMillis() - 60_000;
            }

            GetGemsHistoryDto response = apiClient.getHistory(
                    lastTime, null, 50, null, TARGET_TYPES, false
            );

            if (response == null || !response.isSuccess() || response.getResponse().getItems().isEmpty()) {
                return;
            }

            var items = response.getResponse().getItems();

            // Фильтрация и маппинг
            List<GiftHistoryDocument> newEntities = items.stream()
                    .filter(item -> !repository.existsByHash(item.getHash()))
                    .map(mapper::toHistoryEntity)
                    .toList();

            if (!newEntities.isEmpty()) {
                repository.saveAll(newEntities); // Batch insert
                log.info("Saved {} NEW events", newEntities.size());
            }

            long maxTimestamp = items.stream()
                    .mapToLong(i -> i.getTimestamp() != null ? i.getTimestamp() : 0L)
                    .max()
                    .orElse(lastTime);

            stateService.updateState(PROCESS_ID, maxTimestamp, null);

        } catch (Exception e) {
            log.error("Realtime parser error: {}", e.getMessage());
        }
    }
}