package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsHistoryDto;
import com.ceawse.blinkgift.dto.GetGemsItemDto;
import com.ceawse.blinkgift.mapper.EventMapper;
import com.ceawse.blinkgift.repository.GiftHistoryRepository;
import com.ceawse.blinkgift.service.StateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetGemsRealtimeParser {

    private final GetGemsApiClient apiClient;
    private final GiftHistoryRepository repository;
    private final StateService stateService;
    private final EventMapper mapper;

    private static final String PROCESS_ID = "GETGEMS_LIVE";
    // Типы событий, которые мы слушаем
    private static final List<String> TARGET_TYPES = List.of("sold", "cancelSale", "putUpForSale");

    @Scheduled(fixedDelay = 3000)
    public void poll() {
        try {
            long lastTime = stateService.getState(PROCESS_ID).getLastProcessedTimestamp();
            // Если база пустая, берем небольшой запас назад
            if (lastTime == 0) {
                lastTime = System.currentTimeMillis() - 60_000;
            }

            GetGemsHistoryDto response = apiClient.getHistory(
                    lastTime,       // minTime (включая эту миллисекунду)
                    null,
                    50,
                    null,
                    TARGET_TYPES,
                    false
            );

            if (response == null || !response.isSuccess() || response.getResponse().getItems().isEmpty()) {
                return;
            }

            List<GetGemsItemDto> items = response.getResponse().getItems();
            log.info("Получено {} событий (кандидаты)", items.size());

            // ФИЛЬТРАЦИЯ ДУБЛИКАТОВ
            // Мы могли получить события, которые уже есть в БД (из-за оверлапа времени)
            List<GiftHistoryDocument> newEntities = items.stream()
                    .filter(item -> !repository.existsByHash(item.getHash())) // Проверяем наличие
                    .map(mapper::toEntity)
                    .toList();

            if (!newEntities.isEmpty()) {
                repository.saveAll(newEntities);
                log.info("Сохранено {} НОВЫХ событий", newEntities.size());
            }

            // Обновляем курсор времени
            // Берем максимальное время из полученной пачки БЕЗ прибавления единицы
            long maxTimestampInBatch = items.stream()
                    .mapToLong(item -> item.getTimestamp() != null ? item.getTimestamp() : 0L)
                    .max()
                    .orElse(lastTime);

            // Сохраняем "чистое" время. В след. раз начнем с него же и отфильтруем дубли.
            stateService.updateState(PROCESS_ID, maxTimestampInBatch, null);

        } catch (Exception e) {
            log.error("Ошибка парсинга GetGems: {}", e.getMessage());
        }
    }
}