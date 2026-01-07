package com.ceawse.coreprocessor.worker;

import com.ceawse.coreprocessor.model.GiftHistoryDocument;
import com.ceawse.coreprocessor.model.ProcessorState;
import com.ceawse.coreprocessor.repository.ProcessorStateRepository;
import com.ceawse.coreprocessor.service.MarketStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessorWorker {

    private final ProcessorStateRepository stateRepository;
    private final MarketStateMachine stateMachine;
    private final MongoTemplate mongoTemplate;

    private static final String PROCESSOR_ID = "MAIN_PROCESSOR";
    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelay = 1000)
    public void processBatch() {
        ProcessorState state = stateRepository.findById(PROCESSOR_ID)
                .orElse(new ProcessorState(PROCESSOR_ID, 0L, null));

        long lastTime = state.getLastProcessedTimestamp();
        String lastId = state.getLastProcessedId();

        Query query = new Query();
        // ИСПРАВЛЕНИЕ: Используем gte (больше или равно), чтобы не терять события с тем же timestamp
        query.addCriteria(Criteria.where("timestamp").gte(lastTime));
        // ВАЖНО: Сортируем по времени И по ID для детерминированного порядка
        query.with(Sort.by(Sort.Direction.ASC, "timestamp", "id"));
        query.limit(BATCH_SIZE);

        List<GiftHistoryDocument> events = mongoTemplate.find(query, GiftHistoryDocument.class);

        if (events.isEmpty()) {
            return;
        }

        log.debug("Processing batch of {} events...", events.size());

        long maxTimeInBatch = lastTime;
        String maxIdInBatch = lastId;
        boolean stateChanged = false;

        for (GiftHistoryDocument event : events) {
            // ФИЛЬТРАЦИЯ: Пропускаем события, которые мы уже видели (тот же timestamp и id <= сохраненного)
            if (event.getTimestamp() == lastTime && lastId != null && event.getId().compareTo(lastId) <= 0) {
                continue;
            }

            try {
                stateMachine.applyEvent(event);

                // Обновляем курсоры текущего батча
                maxTimeInBatch = event.getTimestamp();
                maxIdInBatch = event.getId();
                stateChanged = true;
            } catch (Exception e) {
                log.error("Error processing event {}: {}", event.getId(), e.getMessage());
                // В продакшене тут нужен Dead Letter Queue. Сейчас пропускаем событие, но двигаем курсор,
                // чтобы не застрять вечно на одной ошибке.
                maxTimeInBatch = event.getTimestamp();
                maxIdInBatch = event.getId();
                stateChanged = true;
            }
        }

        if (stateChanged) {
            state.setLastProcessedTimestamp(maxTimeInBatch);
            state.setLastProcessedId(maxIdInBatch);
            stateRepository.save(state);
        }
    }
}