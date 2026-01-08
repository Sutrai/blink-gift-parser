package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument.GiftAttributes;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncGiftAttributesWorker {

    private final UniqueGiftRepository uniqueGiftRepository;
    private final MongoTemplate mongoTemplate;
    private final WebClient webClient = WebClient.create();
    private static final int BATCH_SIZE = 500;
    private static final int CONCURRENCY = 50;

    @Scheduled(fixedDelay = 10000)
    public void updateGiftAttributesAsync() {
        Query query = new Query();
        query.addCriteria(Criteria.where("attributes").exists(false));
        query.limit(BATCH_SIZE);
        List<UniqueGiftDocument> gifts = mongoTemplate.find(query, UniqueGiftDocument.class);
        if (gifts.isEmpty()) return;

        log.info("Async updating attributes for {} gifts...", gifts.size());

        AtomicInteger processed = new AtomicInteger();

        Flux.fromIterable(gifts)
                .parallel(CONCURRENCY)
                .runOn(Schedulers.boundedElastic())
                .flatMap(gift -> webClient.get()
                        .uri("https://nft.fragment.com/gift/{id}.json", gift.getId())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .map(json -> {
                            if (json == null || !json.has("attributes")) return null;

                            JsonNode attrs = json.get("attributes");
                            GiftAttributes ga = GiftAttributes.builder()
                                    .model(attrs.get(0).get("value").asText())
                                    .backdrop(attrs.get(1).get("value").asText())
                                    .symbol(attrs.get(2).get("value").asText())
                                    .updatedAt(Instant.now())
                                    .build();

                            Update update = new Update()
                                    .set("attributes", ga)
                                    .set("lottie", json.has("lottie") ? json.get("lottie").asText() : null);

                            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(gift.getId())), update, UniqueGiftDocument.class);
                            processed.incrementAndGet();
                            return gift.getId();
                        })
                        .onErrorResume(e -> {
                            log.warn("Failed to update attributes for gift {}: {}", gift.getId(), e.getMessage());
                            return Flux.empty().singleOrEmpty();
                        })
                )
                .sequential()
                .doOnComplete(() -> log.info("Async attributes update finished. Processed {} gifts.", processed.get()))
                .subscribe();
    }
}
