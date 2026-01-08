package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.client.GiftAttributesFeignClient;
import com.ceawse.giftdiscovery.dto.GiftAttributesDto;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument.GiftAttributes;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SafeGiftAttributesWorker {

    private final UniqueGiftRepository uniqueGiftRepository;
    private final MongoTemplate mongoTemplate;
    private final GiftAttributesFeignClient getGemsApiClient; // Feign клиент

    private static final int BATCH_SIZE = 500;
    private static final int THREAD_POOL_SIZE = 10;

    @Scheduled(fixedDelay = 15000)
    public void updateGiftAttributesSafe() {
        Query query = new Query(Criteria.where("attributes").exists(false));
        query.limit(BATCH_SIZE);
        List<UniqueGiftDocument> gifts = mongoTemplate.find(query, UniqueGiftDocument.class);
        if (gifts.isEmpty()) return;

        log.info("Processing {} gifts in safe mode...", gifts.size());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (UniqueGiftDocument gift : gifts) {
            executor.submit(() -> {
                try {
                    String formattedName = gift.getName().replace(" ", "").replace("#", "-");

                    // Пример запроса через Feign (можно заменить на Fragment API)
                    GiftAttributesDto attributesDto = getGemsApiClient.getAttributes(formattedName);

                    if (attributesDto != null && attributesDto.getAttributes() != null) {
                        List<GiftAttributesDto.AttributeDto> attrs = attributesDto.getAttributes();
                        UniqueGiftDocument.GiftAttributes ga = UniqueGiftDocument.GiftAttributes.builder()
                                .model(attrs.get(0).getValue())
                                .backdrop(attrs.get(1).getValue())
                                .symbol(attrs.get(2).getValue())
                                .updatedAt(Instant.now())
                                .build();

                        Update update = new Update().set("attributes", ga);
                        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(gift.getId())), update, UniqueGiftDocument.class);
                    }

                } catch (Exception e) {
                    log.warn("Failed to update gift {}: {}", gift.getId(), e.getMessage());
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.error("Executor interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        log.info("Finished batch processing of {} gifts.", gifts.size());
    }
}

