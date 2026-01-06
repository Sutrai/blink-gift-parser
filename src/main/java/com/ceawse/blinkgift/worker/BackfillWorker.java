package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsHistoryDto;
import com.ceawse.blinkgift.mapper.EventMapper;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import com.ceawse.blinkgift.service.StateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackfillWorker {

    private final GetGemsApiClient apiClient;
    private final GiftHistoryRepository historyRepository;
    private final StateService stateService;
    private final EventMapper mapper;

    private static final String PROCESS_ID = "GETGEMS_BACKFILL";
    // Дата запуска подарков в Telegram (октябрь 2024). Раньше искать смысла нет.
    private static final long START_EPOCH_MS = 1727740800000L;
    private static final long WINDOW_SIZE_MS = 3600 * 1000; // 1 час

    // Запускаем вручную или при старте, если флаг включен
    // Можно поставить @Scheduled(initialDelay...)
    public void runBackfill() {
        log.info("Starting Backfill Process...");

        var state = stateService.getState(PROCESS_ID);
        if ("FINISHED".equals(state.getStatus())) {
            log.info("Backfill already finished.");
            return;
        }

        long currentWindowStart = state.getLastProcessedTimestamp() > 0
                ? state.getLastProcessedTimestamp()
                : START_EPOCH_MS;

        long now = System.currentTimeMillis();

        while (currentWindowStart < now) {
            long currentWindowEnd = currentWindowStart + WINDOW_SIZE_MS;
            if (currentWindowEnd > now) currentWindowEnd = now;

            log.info("Processing window: {} to {}", Instant.ofEpochMilli(currentWindowStart), Instant.ofEpochMilli(currentWindowEnd));

            processTimeWindow(currentWindowStart, currentWindowEnd);

            // Сохраняем прогресс (чекпоинт - конец окна)
            stateService.updateState(PROCESS_ID, currentWindowEnd, null);
            currentWindowStart = currentWindowEnd;
        }

        stateService.markFinished(PROCESS_ID);
        log.info("Backfill Completed!");
    }

    private void processTimeWindow(long start, long end) {
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                // Запрос с ограничением по времени (окно) и курсором (пагинация внутри окна)
                GetGemsHistoryDto response = apiClient.getHistory(start, end, 100, cursor);

                if (response == null || !response.isSuccess() || response.getResponse().getItems().isEmpty()) {
                    hasMore = false;
                    continue;
                }

                List<GiftHistoryDocument> entities = response.getResponse().getItems().stream()
                        .map(mapper::toEntity)
                        .toList();

                // Сохраняем пачку. Mongo saveAll работает быстро.
                // В реальном проекте тут нужен catch DuplicateKeyException
                historyRepository.saveAll(entities);

                cursor = response.getResponse().getCursor();
                if (cursor == null) {
                    hasMore = false;
                }

                // Анти-спам задержка
                Thread.sleep(200);

            } catch (Exception e) {
                log.error("Error in backfill window {} - {}", start, end, e);
                // Тут стратегия: либо ретрай, либо пропуск. Для надежности лучше ретрай.
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }
}