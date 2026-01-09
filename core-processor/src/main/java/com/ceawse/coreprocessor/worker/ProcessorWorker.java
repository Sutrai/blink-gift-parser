package com.ceawse.coreprocessor.worker;

import com.ceawse.coreprocessor.model.GiftHistoryDocument;
import com.ceawse.coreprocessor.model.ProcessorState;
import com.ceawse.coreprocessor.repository.ProcessorStateRepository;
import com.ceawse.coreprocessor.service.MarketProcessor;
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
    private final MarketProcessor marketProcessor;
    private final MongoTemplate mongoTemplate;

    private static final String PROCESSOR_ID = "MAIN_PROCESSOR";
    private static final int BATCH_SIZE = 1000;

    @Scheduled(fixedDelayString = "${app.worker.delay:1000}")
    public void processBatch() {
        ProcessorState state = getOrInitState();

        List<GiftHistoryDocument> events = fetchNextBatch(state);

        if (events.isEmpty()) {
            return;
        }

        log.debug("Processing batch of {} events. Start form: timestamp={}, id={}",
                events.size(), state.getLastProcessedTimestamp(), state.getLastProcessedId());

        long maxTime = state.getLastProcessedTimestamp();
        String maxId = state.getLastProcessedId();
        boolean stateUpdated = false;

        for (GiftHistoryDocument event : events) {
            try {
                marketProcessor.processEvent(event);
            } catch (Exception e) {
                log.error("Error processing event ID={}: {}", event.getId(), e.getMessage(), e);
            } finally {
                maxTime = event.getTimestamp();
                maxId = event.getId();
                stateUpdated = true;
            }
        }

        if (stateUpdated) {
            updateState(state, maxTime, maxId);
        }
    }

    private ProcessorState getOrInitState() {
        return stateRepository.findById(PROCESSOR_ID)
                .orElse(new ProcessorState(PROCESSOR_ID, 0L, null));
    }

    private void updateState(ProcessorState state, long timestamp, String id) {
        state.setLastProcessedTimestamp(timestamp);
        state.setLastProcessedId(id);
        stateRepository.save(state);
        log.info("Batch completed. New state: timestamp={}, id={}", timestamp, id);
    }

    private List<GiftHistoryDocument> fetchNextBatch(ProcessorState state) {
        Query query = new Query();
        long lastTime = state.getLastProcessedTimestamp();
        String lastId = state.getLastProcessedId();

        if (lastId != null) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("timestamp").gt(lastTime),
                    Criteria.where("timestamp").is(lastTime).and("id").gt(lastId)
            ));
        } else {
            query.addCriteria(Criteria.where("timestamp").gte(lastTime));
        }

        query.with(Sort.by(Sort.Direction.ASC, "timestamp", "id"));
        query.limit(BATCH_SIZE);

        return mongoTemplate.find(query, GiftHistoryDocument.class);
    }
}