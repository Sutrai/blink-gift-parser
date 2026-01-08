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

        // ВАЖНОЕ ИЗМЕНЕНИЕ: Правильная пагинация (Keyset Pagination)
        // Если у нас есть lastId, мы ищем:
        // ЛИБО (время > последнего), ЛИБО (время == последнему И id > последнего)
        if (lastId != null) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("timestamp").gt(lastTime),
                    Criteria.where("timestamp").is(lastTime).and("id").gt(lastId)
            ));
        } else {
            // Первый запуск или сброс
            query.addCriteria(Criteria.where("timestamp").gte(lastTime));
        }

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
            // Удаляем старую проверку if (... continue), так как запрос к БД теперь гарантирует новые данные

            try {
                stateMachine.applyEvent(event);

                maxTimeInBatch = event.getTimestamp();
                maxIdInBatch = event.getId();
                stateChanged = true;
            } catch (Exception e) {
                log.error("CRITICAL ERROR processing event ID={}. Stopping batch. Error: {}", event.getId(), e.getMessage());
                // Останавливаем батч, но сохраняем прогресс до ошибки
                break;
            }
        }

        if (stateChanged) {
            state.setLastProcessedTimestamp(maxTimeInBatch);
            state.setLastProcessedId(maxIdInBatch);
            stateRepository.save(state);
            log.info("Batch processed. New state: time={}, id={}", maxTimeInBatch, maxIdInBatch);
        }
    }
}