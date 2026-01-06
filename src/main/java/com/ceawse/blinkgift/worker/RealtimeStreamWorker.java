package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsHistoryDto;
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
public class RealtimeStreamWorker {

    private final GetGemsApiClient apiClient;
    private final GiftHistoryRepository historyRepository;
    private final StateService stateService;
    private final EventMapper mapper;

    private static final String PROCESS_ID = "GETGEMS_REALTIME";

    // Запускаем раз в 5 секунд
    @Scheduled(fixedDelay = 5000)
    public void pollNewEvents() {
        try {
            var state = stateService.getState(PROCESS_ID);

            // Если мы еще ни разу не запускали реалтайм, начнем с "сейчас" минус пару минут
            long minTime = state.getLastProcessedTimestamp() > 0
                    ? state.getLastProcessedTimestamp()
                    : (System.currentTimeMillis() - 600000); // 10 минут назад

            // Запрашиваем всё, что новее minTime. Без maxTime.
            GetGemsHistoryDto response = apiClient.getHistory(minTime, null, 100, null);

            if (response != null && response.isSuccess() && !response.getResponse().getItems().isEmpty()) {

                var items = response.getResponse().getItems();
                List<GiftHistoryDocument> entities = items.stream()
                        .map(mapper::toEntity)
                        .toList();

                // ВАЖНО: Тут могут быть дубли, так как мы запрашиваем с minTime включительно.
                // saveAll сделает update если ID совпадет (если _id настроен правильно)
                // Или используем insert с игнором ошибок.
                historyRepository.saveAll(entities);

                // Находим максимальное время в полученной пачке
                long maxTimestampInBatch = items.stream()
                        .mapToLong(item -> item.getTimestamp() != null ? item.getTimestamp() : 0L)
                        .max()
                        .orElse(minTime);

                // +1 мс, чтобы в следующий раз не брать то же самое событие (если гетгемс позволяет строгий >)
                // Если API включает границу, то +1 нужен. Если нет - то нет. Обычно безопасно не добавлять, но фильтровать дубли по хэшу.
                stateService.updateState(PROCESS_ID, maxTimestampInBatch, null);

                log.info("Realtime: Processed {} events. New head: {}", items.size(), maxTimestampInBatch);
            }

        } catch (Exception e) {
            log.error("Realtime polling failed", e);
        }
    }
}