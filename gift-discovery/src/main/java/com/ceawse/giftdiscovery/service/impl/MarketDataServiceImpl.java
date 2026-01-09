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

    // Используем AtomicReference для безопасной замены карты целиком (Copy-On-Write pattern)
    private final AtomicReference<Map<String, String>> giftToCollectionMap = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, BigDecimal>> collectionFloorMap = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, MarketAttributeDataDto>> attributeDataMap = new AtomicReference<>(Map.of());

    @Override
    public void refreshCache() {
        log.info("Starting market data cache refresh...");
        long start = System.currentTimeMillis();

        refreshCollections();
        refreshStats();
        refreshAttributes();

        log.info("Market data refreshed in {} ms.", System.currentTimeMillis() - start);
    }

    private void refreshCollections() {
        var collections = mongoTemplate.findAll(CollectionRegistryDocument.class);
        Map<String, String> map = new HashMap<>(collections.size());
        for (var col : collections) {
            if (col.getName() != null) {
                map.put(col.getName().trim().toLowerCase(), col.getAddress());
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
        if (providedAddress != null && providedAddress.startsWith("EQ")) {
            return providedAddress;
        }
        if (giftName == null) return null;

        String collectionName = giftName.split("#")[0].trim().toLowerCase();
        return giftToCollectionMap.get().get(collectionName);
    }

    @Override
    public BigDecimal getCollectionFloor(String collectionAddress) {
        return collectionFloorMap.get().getOrDefault(collectionAddress, BigDecimal.ZERO);
    }

    @Override
    public MarketAttributeDataDto getAttributeData(String collectionAddress, String traitType, String value) {
        if (collectionAddress == null || traitType == null || value == null) return null;
        String key = CollectionAttributeDocument.generateId(collectionAddress, traitType, value);
        return attributeDataMap.get().get(key);
    }
}