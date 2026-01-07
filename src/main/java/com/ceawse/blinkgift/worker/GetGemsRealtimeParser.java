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

    @Scheduled(fixedDelay = 3000) // Раз в 3 секунды
    public void poll() {
        try {
            // 1. Определяем, с какого времени читать
            // Если в БД пусто (первый запуск), берем "сейчас" минус 10 секунд (на всякий случай)
            long lastTime = stateService.getState(PROCESS_ID).getLastProcessedTimestamp();
            if (lastTime == 0) {
                lastTime = System.currentTimeMillis() - 10_000;
            }

            // 2. Делаем запрос
            GetGemsHistoryDto response = apiClient.getHistory(
                    lastTime,       // minTime
                    null,           // maxTime (null = до бесконечности)
                    50,             // limit (хватит для реалтайма)
                    null,           // cursor (для первого прохода не нужен, если часто поллим)
                    TARGET_TYPES,   // types
                    false           // reverse
            );

            if (response == null || !response.isSuccess() || response.getResponse().getItems().isEmpty()) {
                return; // Новых событий нет
            }

            List<GetGemsItemDto> items = response.getResponse().getItems();
            log.info("Получено {} новых событий", items.size());

            // 3. Маппим и сохраняем
            List<GiftHistoryDocument> entities = items.stream()
                    .map(mapper::toEntity)
                    .toList();

            repository.saveAll(entities);

            // 4. Обновляем курсор времени
            // Ищем самое свежее событие в пачке
            long maxTimestampInBatch = items.stream()
                    .mapToLong(item -> item.getTimestamp() != null ? item.getTimestamp() : 0L)
                    .max()
                    .orElse(lastTime);

            // Сохраняем maxTimestamp + 1мс, чтобы в следующий раз не получать дубли
            stateService.updateState(PROCESS_ID, maxTimestampInBatch + 1, null);

        } catch (Exception e) {
            log.error("Ошибка парсинга GetGems: {}", e.getMessage());
            // Не останавливаемся, просто ждем следующий тик
        }
    }
}