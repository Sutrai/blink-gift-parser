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
    private final MongoTemplate mongoTemplate; // Используем Template для гибких запросов

    private static final String PROCESSOR_ID = "MAIN_PROCESSOR";
    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelay = 1000) // Запускаем каждую секунду
    public void processBatch() {
        // 1. Узнаем, где остановились
        ProcessorState state = stateRepository.findById(PROCESSOR_ID)
                .orElse(new ProcessorState(PROCESSOR_ID, 0L));

        long lastTime = state.getLastProcessedTimestamp();

        // 2. Читаем новые события (timestamp > lastTime), сортируем от старых к новым
        Query query = new Query();
        query.addCriteria(Criteria.where("timestamp").gt(lastTime));
        query.with(Sort.by(Sort.Direction.ASC, "timestamp")); // Важно: хронологический порядок!
        query.limit(BATCH_SIZE);

        List<GiftHistoryDocument> events = mongoTemplate.find(query, GiftHistoryDocument.class);

        if (events.isEmpty()) {
            return;
        }

        log.debug("Processing batch of {} events...", events.size());

        // 3. Применяем события
        long maxTimeInBatch = lastTime;
        for (GiftHistoryDocument event : events) {
            try {
                stateMachine.applyEvent(event);

                if (event.getTimestamp() > maxTimeInBatch) {
                    maxTimeInBatch = event.getTimestamp();
                }
            } catch (Exception e) {
                log.error("Error processing event {}: {}", event.getId(), e.getMessage());
                // В реальном проде тут нужна Dead Letter Queue, пока просто логируем
            }
        }

        // 4. Сохраняем прогресс
        state.setLastProcessedTimestamp(maxTimeInBatch);
        stateRepository.save(state);
    }
}