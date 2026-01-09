package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.service.MarketDataCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceEstimationWorker {

    private final MongoTemplate mongoTemplate;
    private final MarketDataCacheService cacheService;

    private static final int BATCH_SIZE = 1000;

    @Scheduled(fixedDelay = 2000)
    public void updateGiftPrices() {
        // Ищем подарки, у которых есть атрибуты, но цена еще не посчитана или устарела (опционально)
        // Для простоты берем те, где estimatedPrice еще нет.
        // Чтобы обновлять цены периодически, можно добавить условие "или priceUpdatedAt < 1 hour ago"
        Query query = new Query(Criteria.where("attributes").exists(true)
                .and("marketData.estimatedPrice").exists(false));

        query.limit(BATCH_SIZE);

        List<UniqueGiftDocument> gifts = mongoTemplate.find(query, UniqueGiftDocument.class);

        if (gifts.isEmpty()) {
            // Если все новые обработаны, можно взять старые для обновления (раскомментировать при необходимости)
            // query = new Query(Criteria.where("attributes").exists(true)).with(Sort.by(Sort.Direction.ASC, "marketData.priceUpdatedAt")).limit(BATCH_SIZE);
            // gifts = mongoTemplate.find(query, UniqueGiftDocument.class);
            return;
        }

        log.info("Estimating prices for {} gifts...", gifts.size());

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UniqueGiftDocument.class);
        int updatesCount = 0;

        for (UniqueGiftDocument gift : gifts) {
            // 1. Определяем реальный адрес коллекции (фикс для Portals)
            String collectionAddress = cacheService.resolveCollectionAddress(gift.getName(), gift.getCollectionAddress());

            if (collectionAddress == null) {
                // Если не нашли коллекцию, не можем посчитать цены
                continue;
            }

            // 2. Получаем Floor коллекции
            BigDecimal floorPrice = cacheService.getCollectionFloor(collectionAddress);

            // 3. Обогащаем атрибуты ценами и редкостью
            UniqueGiftDocument.GiftAttributes attrs = gift.getAttributes();
            if (attrs == null) continue;

            // Обработка Model
            var modelData = cacheService.getAttributeData(collectionAddress, "Model", attrs.getModel());
            if (modelData != null) {
                attrs.setModelPrice(modelData.getPrice());
                attrs.setModelRarityCount(modelData.getCount());
            }

            // Обработка Backdrop
            var backdropData = cacheService.getAttributeData(collectionAddress, "Backdrop", attrs.getBackdrop());
            if (backdropData != null) {
                attrs.setBackdropPrice(backdropData.getPrice());
                attrs.setBackdropRarityCount(backdropData.getCount());
            }

            // Обработка Symbol
            var symbolData = cacheService.getAttributeData(collectionAddress, "Symbol", attrs.getSymbol());
            if (symbolData != null) {
                attrs.setSymbolPrice(symbolData.getPrice());
                attrs.setSymbolRarityCount(symbolData.getCount());
            }

            // 4. Формула расчета
            // (floor * 2) + modelPrice + backdropPrice) / 4
            BigDecimal modelP = attrs.getModelPrice() != null ? attrs.getModelPrice() : floorPrice;
            BigDecimal backdropP = attrs.getBackdropPrice() != null ? attrs.getBackdropPrice() : floorPrice;

            // Если у атрибута нет цены (редкий или не торгуется), используем Floor как fallback или 0?
            // Обычно, если нет листингов, цена атрибута >= Floor. Используем Floor для консервативности.

            BigDecimal numerator = floorPrice.multiply(BigDecimal.valueOf(2))
                    .add(modelP)
                    .add(backdropP);

            BigDecimal estimatedPrice = numerator.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);

            // 5. Подготовка апдейта
            Update update = new Update();

            // Обновляем вложенные поля атрибутов
            update.set("attributes.modelPrice", attrs.getModelPrice());
            update.set("attributes.modelRarityCount", attrs.getModelRarityCount());

            update.set("attributes.backdropPrice", attrs.getBackdropPrice());
            update.set("attributes.backdropRarityCount", attrs.getBackdropRarityCount());

            update.set("attributes.symbolPrice", attrs.getSymbolPrice());
            update.set("attributes.symbolRarityCount", attrs.getSymbolRarityCount());

            // Обновляем рыночные данные
            update.set("marketData.collectionFloorPrice", floorPrice);
            update.set("marketData.estimatedPrice", estimatedPrice);
            update.set("marketData.priceUpdatedAt", Instant.now());

            // Если адрес коллекции был исправлен (например, с UUID на EQ), обновим его
            if (!collectionAddress.equals(gift.getCollectionAddress())) {
                update.set("collectionAddress", collectionAddress);
            }

            bulkOps.updateOne(new Query(Criteria.where("_id").is(gift.getId())), update);
            updatesCount++;
        }

        if (updatesCount > 0) {
            bulkOps.execute();
            log.info("Batch completed. Updated prices for {} gifts.", updatesCount);
        }
    }
}