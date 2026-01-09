package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.client.GiftAttributesFeignClient;
import com.ceawse.giftdiscovery.client.TelegramNftFeignClient;
import com.ceawse.giftdiscovery.dto.GiftAttributesDto;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository; // Не используется напрямую, но пусть будет
import feign.FeignException;
import feign.codec.DecodeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SafeGiftAttributesWorker {

    private final MongoTemplate mongoTemplate;
    private final GiftAttributesFeignClient fragmentApiClient;
    private final TelegramNftFeignClient telegramApiClient;

    // УВЕЛИЧИВАЕМ БАТЧ И ПОТОКИ
    private static final int BATCH_SIZE = 1000; // Было 500
    private static final int THREAD_POOL_SIZE = 40; // Было 10. Ставим 40-50, пока прокси держит.

    // Регулярки (без изменений)
    private static final Pattern OG_DESC_PATTERN = Pattern.compile("<meta property=\"og:description\" content=\"([\\s\\S]*?)\">");
    private static final Pattern ATTR_MODEL_PATTERN = Pattern.compile("Model:\\s*(.*?)(?:\\n|$)");
    private static final Pattern ATTR_BACKDROP_PATTERN = Pattern.compile("Backdrop:\\s*(.*?)(?:\\n|$)");
    private static final Pattern ATTR_SYMBOL_PATTERN = Pattern.compile("Symbol:\\s*(.*?)(?:\\n|$)");

    // УМЕНЬШАЕМ ЗАДЕРЖКУ (fixedDelay)
    // 1000 мс = 1 секунда отдыха между пачками. Было 15 сек.
    @Scheduled(fixedDelay = 1000)
    public void updateGiftAttributesSafe() {
        Query query = new Query(Criteria.where("attributes").exists(false));
        query.limit(BATCH_SIZE);
        List<UniqueGiftDocument> gifts = mongoTemplate.find(query, UniqueGiftDocument.class);

        if (gifts.isEmpty()) return;

        log.info("Processing {} gifts with {} threads...", gifts.size(), THREAD_POOL_SIZE);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Потокобезопасная очередь для сбора успешных результатов
        ConcurrentLinkedQueue<UniqueGiftDocument> successUpdates = new ConcurrentLinkedQueue<>();

        for (UniqueGiftDocument gift : gifts) {
            executor.submit(() -> {
                try {
                    String slug = formatSlug(gift.getName());
                    UniqueGiftDocument.GiftAttributes attributes;

                    if (gift.isOffchainSafe()) {
                        String html = telegramApiClient.getNftPage(slug);
                        attributes = parseAttributesFromHtml(html);
                    } else {
                        GiftAttributesDto dto = fragmentApiClient.getAttributes(slug);
                        attributes = mapFragmentDtoToModel(dto);
                    }

                    if (attributes != null) {
                        gift.setAttributes(attributes); // Обновляем объект в памяти
                        successUpdates.add(gift);       // Добавляем в очередь на сохранение
                    }

                } catch (DecodeException e) {
                    log.error("Decode Error '{}': {}", gift.getName(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                } catch (FeignException e) {
                    log.warn("HTTP Error '{}': {}", gift.getName(), e.status());
                } catch (Exception e) {
                    log.error("Error '{}': {}", gift.getName(), e.getMessage());
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // --- BULK UPDATE В БАЗУ ДАННЫХ ---
        if (!successUpdates.isEmpty()) {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UniqueGiftDocument.class);

            for (UniqueGiftDocument updatedGift : successUpdates) {
                Query q = new Query(Criteria.where("_id").is(updatedGift.getId()));
                Update u = new Update().set("attributes", updatedGift.getAttributes());
                bulkOps.updateOne(q, u);
            }

            var result = bulkOps.execute();
            log.info("Batch finished. Updated in DB: {}", result.getModifiedCount());
        } else {
            log.info("Batch finished. No updates to save.");
        }
    }

    private String formatSlug(String name) {
        if (name == null) return "";

        int hashIndex = name.lastIndexOf('#');

        if (hashIndex != -1) {
            String namePart = name.substring(0, hashIndex);

            String numberPart = name.substring(hashIndex + 1).trim();

            String cleanName = namePart
                    .replace(" ", "")
                    .replace("'", "")
                    .replace("’", "")
                    .replace("-", "")
                    .trim();

            return cleanName + "-" + numberPart;
        }

        // Если решетки нет (фоллбэк для странных данных)
        return name.replace(" ", "").replace("'", "").replace("’", "").replace("-", "");
    }

    private UniqueGiftDocument.GiftAttributes mapFragmentDtoToModel(GiftAttributesDto dto) {
        if (dto == null || dto.getAttributes() == null) return null;
        // Безопасный доступ по индексам или стримам
        var attrs = dto.getAttributes();
        return UniqueGiftDocument.GiftAttributes.builder()
                .model(attrs.size() > 0 ? attrs.get(0).getValue() : null)
                .backdrop(attrs.size() > 1 ? attrs.get(1).getValue() : null)
                .symbol(attrs.size() > 2 ? attrs.get(2).getValue() : null)
                .updatedAt(Instant.now())
                .build();
    }

    private UniqueGiftDocument.GiftAttributes parseAttributesFromHtml(String html) {
        Matcher descMatcher = OG_DESC_PATTERN.matcher(html);
        if (descMatcher.find()) {
            String content = descMatcher.group(1);
            String model = extractValue(content, ATTR_MODEL_PATTERN);
            String backdrop = extractValue(content, ATTR_BACKDROP_PATTERN);
            String symbol = extractValue(content, ATTR_SYMBOL_PATTERN);

            if (model == null && backdrop == null && symbol == null) return null;

            return UniqueGiftDocument.GiftAttributes.builder()
                    .model(model).backdrop(backdrop).symbol(symbol)
                    .updatedAt(Instant.now()).build();
        }
        return null;
    }

    private String extractValue(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}