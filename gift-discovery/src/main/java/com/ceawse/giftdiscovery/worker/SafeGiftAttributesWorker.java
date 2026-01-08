package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.client.GiftAttributesFeignClient;
import com.ceawse.giftdiscovery.dto.GiftAttributesDto;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SafeGiftAttributesWorker {

    private final MongoTemplate mongoTemplate;
    private final GiftAttributesFeignClient getGemsApiClient;

    private static final int BATCH_SIZE = 500;
    private static final int THREAD_POOL_SIZE = 10;

    @Scheduled(fixedDelay = 15000)
    public void updateGiftAttributesSafe() {
        // ИЗМЕНЕНИЕ ЗДЕСЬ: Добавлено условие isOffchain = false
        Query query = new Query(
                Criteria.where("attributes").exists(false)
                        .and("isOffchain").is(false)
        );
        query.limit(BATCH_SIZE);

        List<UniqueGiftDocument> gifts = mongoTemplate.find(query, UniqueGiftDocument.class);
        if (gifts.isEmpty()) return;

        log.info("Processing {} ON-CHAIN gifts in safe mode...", gifts.size());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (UniqueGiftDocument gift : gifts) {
            executor.submit(() -> {
                try {
                    // Форматирование имени: "Witch Hat #17607" -> "WitchHat-17607"
                    String formattedName = gift.getName().replace(" ", "").replace("#", "-");

                    GiftAttributesDto attributesDto = getGemsApiClient.getAttributes(formattedName);

                    if (attributesDto != null && attributesDto.getAttributes() != null) {
                        List<GiftAttributesDto.AttributeDto> attrs = attributesDto.getAttributes();

                        // Дополнительная защита, чтобы не упасть, если атрибутов меньше 3
                        if (attrs.size() >= 3) {
                            // ВАЖНО: Тут предполагается строгий порядок [Model, Backdrop, Symbol].
                            // Если API вернет в другом порядке, данные перепутаются.
                            // Лучше искать по trait_type, но для быстрого фикса оставил как у вас.

                            UniqueGiftDocument.GiftAttributes ga = UniqueGiftDocument.GiftAttributes.builder()
                                    .model(attrs.get(0).getValue())
                                    .backdrop(attrs.get(1).getValue())
                                    .symbol(attrs.get(2).getValue())
                                    .updatedAt(Instant.now())
                                    .build();

                            Update update = new Update().set("attributes", ga);
                            mongoTemplate.updateFirst(
                                    Query.query(Criteria.where("_id").is(gift.getId())),
                                    update,
                                    UniqueGiftDocument.class
                            );
                        } else {
                            log.warn("Gift {} returned incomplete attributes list (size: {})", gift.getName(), attrs.size());
                        }
                    }

                } catch (Exception e) {
                    // Логируем formattedName нет, но ID поможет найти проблему
                    log.warn("Failed to update gift {}: {}", gift.getId(), e.getMessage());
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Executor interrupted: {}", e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Finished batch processing of {} gifts.", gifts.size());
    }
}