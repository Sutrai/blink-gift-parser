package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.dto.MarketAttributeDataDto;
import com.ceawse.giftdiscovery.model.read.CollectionAttributeDocument;
import com.ceawse.giftdiscovery.model.read.CollectionRegistryDocument;
import com.ceawse.giftdiscovery.model.read.CollectionStatisticsDocument;
import com.ceawse.giftdiscovery.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private final MongoTemplate mongoTemplate;

    private final AtomicReference<Map<String, String>> giftToCollectionMap = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, BigDecimal>> collectionFloorMap = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, MarketAttributeDataDto>> attributeDataMap = new AtomicReference<>(Map.of());

    @Override
    public void refreshCache() {
        log.info("Starting market data cache refresh...");
        refreshCollections();
        refreshStats();
        refreshAttributes();
        log.info("Market data refreshed.");
    }

    private void refreshCollections() {
        var collections = mongoTemplate.findAll(CollectionRegistryDocument.class);
        Map<String, String> map = new HashMap<>(collections.size());
        for (var col : collections) {
            if (col.getName() != null) {
                // Превращаем "Witch Hats" -> "witchhat", "Happy Brownies" -> "happybrownie"
                String singularName = normalizeToSingular(col.getName());
                map.put(singularName, col.getAddress());
                log.debug("Mapped collection: '{}' -> {}", singularName, col.getAddress());
            }
        }
        giftToCollectionMap.set(map);
    }

    private void refreshStats() {
        var stats = mongoTemplate.findAll(CollectionStatisticsDocument.class);
        Map<String, BigDecimal> map = new HashMap<>(stats.size());
        for (var stat : stats) {
            if (stat.getFloorPrice() != null) {
                map.put(stat.getCollectionAddress(), stat.getFloorPrice());
            }
        }
        collectionFloorMap.set(map);
    }

    private void refreshAttributes() {
        var attrs = mongoTemplate.findAll(CollectionAttributeDocument.class);
        Map<String, MarketAttributeDataDto> map = new HashMap<>(attrs.size());
        for (var attr : attrs) {
            String key = CollectionAttributeDocument.generateId(attr.getCollectionAddress(), attr.getTraitType(), attr.getValue());
            map.put(key, new MarketAttributeDataDto(attr.getPrice(), attr.getItemsCount()));
        }
        attributeDataMap.set(map);
    }

    @Override
    public String resolveCollectionAddress(String giftName, String providedAddress) {
        // 1. Если передан TON-адрес (начинается с EQ) или UUID (содержит дефисы),
        // возвращаем его как есть. Это позволит найти атрибуты по точному ID (UUID).
        if (providedAddress != null && (providedAddress.startsWith("EQ") || isUuid(providedAddress))) {
            return providedAddress;
        }

        // 2. Если адреса нет или это просто название, пытаемся найти маппинг по имени подарка
        if (giftName == null) return null;

        String rawName = giftName.split("#")[0].trim();
        String singularSearchName = normalizeToSingular(rawName);

        String resolved = giftToCollectionMap.get().get(singularSearchName);

        if (resolved == null) {
            // Если не нашли в маппинге, а providedAddress все же был передан (даже если он странный),
            // попробуем использовать его как последний шанс
            return providedAddress;
        }

        return resolved;
    }

    private boolean isUuid(String str) {
        if (str == null) return false;
        // UUID обычно имеет длину 36 символов и содержит дефисы
        return str.length() == 36 && str.contains("-");
    }

    @Override
    public BigDecimal getCollectionFloor(String collectionAddress) {
        if (collectionAddress == null) return BigDecimal.ZERO;
        return collectionFloorMap.get().getOrDefault(collectionAddress, BigDecimal.ZERO);
    }

    @Override
    public MarketAttributeDataDto getAttributeData(String collectionAddress, String traitType, String value) {
        if (collectionAddress == null || traitType == null || value == null) return null;
        String key = CollectionAttributeDocument.generateId(collectionAddress, traitType, value);
        return attributeDataMap.get().get(key);
    }

    /**
     * Превращает названия в "сингулярный" вид для сопоставления.
     * Примеры:
     * "Witch Hats" -> "witchhat"
     * "Witch Hat"  -> "witchhat"
     * "Happy Brownies" -> "happybrownie"
     * "Happy Brownie"  -> "happybrownie"
     */
    private String normalizeToSingular(String name) {
        if (name == null) return "";

        // 1. В нижний регистр и убираем пробелы/тире
        String s = name.toLowerCase().replaceAll("[\\s\\-']", "");

        // 2. Обработка окончаний (специфично для подарков Telegram)
        if (s.endsWith("ies")) {
            // Brownies -> brownie
            return s.substring(0, s.length() - 3) + "y";
        }
        if (s.endsWith("s")) {
            // Hats -> hat
            // Исключение: если слово само по себе заканчивается на s (но в подарках таких почти нет)
            return s.substring(0, s.length() - 1);
        }

        return s;
    }
}