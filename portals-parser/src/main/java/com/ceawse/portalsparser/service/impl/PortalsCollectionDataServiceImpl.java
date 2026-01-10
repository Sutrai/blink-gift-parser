package com.ceawse.portalsparser.service.impl;

import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.CollectionAttributeDocument;
import com.ceawse.portalsparser.domain.CollectionStatisticsDocument;
import com.ceawse.portalsparser.dto.PortalsFiltersResponseDto;
import com.ceawse.portalsparser.repository.CollectionAttributeRepository;
import com.ceawse.portalsparser.repository.CollectionStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalsCollectionDataServiceImpl {

    private final PortalsApiClient portalsClient;
    private final CollectionAttributeRepository attributeRepository;
    private final CollectionStatisticsRepository statisticsRepository;

    private static final BigDecimal NANO = BigDecimal.valueOf(1_000_000_000);

    public void updateAllPortalsStatistics() {
        try {
            var response = portalsClient.getCollections(100);
            if (response == null || response.getCollections() == null) return;

            for (var col : response.getCollections()) {
                BigDecimal floor = parseBigDecimalSafe(col.getFloorPrice());
                Long floorNano = floor != null ? floor.multiply(NANO).longValue() : null;

                // Исправляем здесь: добавляем .longValue() и 0L
                CollectionStatisticsDocument doc = CollectionStatisticsDocument.builder()
                        .collectionAddress(col.getId())
                        .name(col.getName()) // Добавим имя, раз оно есть в документе
                        .itemsCount(col.getSupply() != null ? col.getSupply().longValue() : 0L)
                        .floorPrice(floor)
                        .floorPriceNano(floorNano)
                        .updatedAt(Instant.now())
                        .build();

                statisticsRepository.save(doc);
            }
            log.info("Updated statistics for {} Portals collections", response.getCollections().size());
        } catch (Exception e) {
            log.error("Error updating Portals statistics", e);
        }
    }

    public void updateAllPortalsAttributes() {
        try {
            var collectionsResponse = portalsClient.getCollections(100);
            if (collectionsResponse == null || collectionsResponse.getCollections() == null) return;

            for (var col : collectionsResponse.getCollections()) {
                processCollectionAttributes(col.getId(), col.getShortName());
                Thread.sleep(300); // Вежливый задержка
            }
        } catch (Exception e) {
            log.error("Error updating Portals attributes cycle", e);
        }
    }

    private void processCollectionAttributes(String collectionId, String shortName) {
        try {
            var filterResponse = portalsClient.getFilters(shortName);
            if (filterResponse == null || filterResponse.getFloorPrices() == null) return;

            var filters = filterResponse.getFloorPrices().get(shortName);
            if (filters == null) return;

            List<CollectionAttributeDocument> docs = new ArrayList<>();
            Instant now = Instant.now();

            // Обрабатываем 3 типа атрибутов Portals
            addAttributes(docs, collectionId, "Model", filters.getModels(), now);
            addAttributes(docs, collectionId, "Symbol", filters.getSymbols(), now);
            addAttributes(docs, collectionId, "Backdrop", filters.getBackdrops(), now);

            if (!docs.isEmpty()) {
                attributeRepository.saveAll(docs);
                log.debug("Saved {} attributes for Portals: {}", docs.size(), shortName);
            }
        } catch (Exception e) {
            log.error("Failed to parse Portals attributes for {}", shortName, e);
        }
    }

    private void addAttributes(List<CollectionAttributeDocument> docs, String address, String type, Map<String, String> values, Instant now) {
        if (values == null) return;
        values.forEach((value, priceStr) -> {
            BigDecimal price = parseBigDecimalSafe(priceStr);
            Long priceNano = price != null ? price.multiply(NANO).longValue() : null;

            docs.add(CollectionAttributeDocument.builder()
                    .id(CollectionAttributeDocument.generateId(address, type, value))
                    .collectionAddress(address)
                    .traitType(type)
                    .value(value)
                    .price(price)
                    .priceNano(priceNano)
                    .currency("TON")
                    .updatedAt(now)
                    .build());
        });
    }

    private BigDecimal parseBigDecimalSafe(String val) {
        if (val == null || val.isBlank()) return null;
        try { return new BigDecimal(val); } catch (Exception e) { return null; }
    }
}