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
        // Исправление #1: GTE для timestamp
        query.addCriteria(Criteria.where("timestamp").gte(lastTime));
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
            // Фильтрация дублей
            if (event.getTimestamp() == lastTime && lastId != null && event.getId().compareTo(lastId) <= 0) {
                continue;
            }

            try {
                stateMachine.applyEvent(event);

                // Успешно обработали - обновляем локальные курсоры
                maxTimeInBatch = event.getTimestamp();
                maxIdInBatch = event.getId();
                stateChanged = true;

            } catch (Exception e) {
                // ИСПРАВЛЕНИЕ #5: Error Handling
                // Если произошла ошибка (БД недоступна, логика сломалась), мы должны ОСТАНОВИТЬСЯ.
                // Нельзя пропускать событие, иначе данные потеряются навсегда.
                log.error("CRITICAL ERROR processing event ID={}. Stopping batch to retry later. Error: {}", event.getId(), e.getMessage());

                // Прерываем цикл.
                // Код ниже (if stateChanged) сохранит прогресс ДО этого события.
                // В следующем запуске @Scheduled мы начнем ровно с этого проблемного события.
                break;
            }
        }

        if (stateChanged) {
            state.setLastProcessedTimestamp(maxTimeInBatch);
            state.setLastProcessedId(maxIdInBatch);
            stateRepository.save(state);
            log.debug("State updated: time={}, id={}", maxTimeInBatch, maxIdInBatch);
        }
    }
}