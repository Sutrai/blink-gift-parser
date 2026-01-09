package com.ceawse.giftdiscovery.service;

import com.ceawse.giftdiscovery.model.read.CollectionAttributeDocument;
import com.ceawse.giftdiscovery.model.read.CollectionRegistryDocument;
import com.ceawse.giftdiscovery.model.read.CollectionStatisticsDocument;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataCacheService {

    private final MongoTemplate mongoTemplate;

    // Кэш: Имя Подарка -> Адрес Коллекции (TON EQ...)
    private Map<String, String> giftNameToCollectionAddress = new HashMap<>();

    // Кэш: Адрес Коллекции -> Floor Price
    private Map<String, BigDecimal> collectionFloors = new ConcurrentHashMap<>();

    // Кэш: ID Атрибута (addr_type_val) -> Данные (цена, кол-во)
    private Map<String, AttributeData> attributeDataMap = new ConcurrentHashMap<>();

    @Getter
    public static class AttributeData {
        private BigDecimal price;
        private Integer count;

        public AttributeData(BigDecimal price, Integer count) {
            this.price = price != null ? price : BigDecimal.ZERO;
            this.count = count != null ? count : 0;
        }
    }

    // Обновляем кэш раз в минуту
    @Scheduled(fixedDelay = 60000)
    public void refreshCache() {
        log.info("Refreshing market data cache...");
        refreshCollections();
        refreshStats();
        refreshAttributes();
        log.info("Market data cache refreshed.");
    }

    private void refreshCollections() {
        List<CollectionRegistryDocument> collections = mongoTemplate.findAll(CollectionRegistryDocument.class);
        Map<String, String> map = new HashMap<>();
        for (CollectionRegistryDocument col : collections) {
            if (col.getName() != null) {
                // Ключ - имя коллекции (например, "Skull Flower")
                map.put(col.getName().trim().toLowerCase(), col.getAddress());
            }
        }
        this.giftNameToCollectionAddress = map;
    }

    private void refreshStats() {
        List<CollectionStatisticsDocument> stats = mongoTemplate.findAll(CollectionStatisticsDocument.class);
        for (CollectionStatisticsDocument stat : stats) {
            if (stat.getFloorPrice() != null) {
                collectionFloors.put(stat.getCollectionAddress(), stat.getFloorPrice());
            }
        }
    }

    private void refreshAttributes() {
        List<CollectionAttributeDocument> attrs = mongoTemplate.findAll(CollectionAttributeDocument.class);
        Map<String, AttributeData> newMap = new ConcurrentHashMap<>();
        for (CollectionAttributeDocument attr : attrs) {
            // Генерируем ID так же, как он записан в БД
            String key = CollectionAttributeDocument.generateId(attr.getCollectionAddress(), attr.getTraitType(), attr.getValue());
            newMap.put(key, new AttributeData(attr.getPrice(), attr.getItemsCount()));
        }
        this.attributeDataMap = newMap;
    }

    // --- Public Lookup Methods ---

    public String resolveCollectionAddress(String giftName, String providedAddress) {
        // Если адрес похож на TON (EQ...), возвращаем его
        if (providedAddress != null && providedAddress.startsWith("EQ")) {
            return providedAddress;
        }
        // Если это UUID (Portals) или пусто, ищем по имени
        if (giftName == null) return null;

        // Извлекаем имя коллекции из имени подарка (например, "Skull Flower #123" -> "skull flower")
        String collectionName = giftName.split("#")[0].trim().toLowerCase();
        return giftNameToCollectionAddress.get(collectionName);
    }

    public BigDecimal getCollectionFloor(String collectionAddress) {
        return collectionFloors.getOrDefault(collectionAddress, BigDecimal.ZERO);
    }

    public AttributeData getAttributeData(String collectionAddress, String traitType, String value) {
        if (collectionAddress == null || traitType == null || value == null) return null;
        String key = CollectionAttributeDocument.generateId(collectionAddress, traitType, value);
        return attributeDataMap.get(key);
    }
}