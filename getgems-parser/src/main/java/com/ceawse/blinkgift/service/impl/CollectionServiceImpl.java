package com.ceawse.blinkgift.service.impl;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.CollectionAttributeDocument;
import com.ceawse.blinkgift.domain.CollectionStatisticsDocument;
import com.ceawse.blinkgift.dto.GetGemsAttributesDto;
import com.ceawse.blinkgift.dto.GetGemsCollectionStatsDto;
import com.ceawse.blinkgift.repository.CollectionAttributeRepository;
import com.ceawse.blinkgift.repository.CollectionStatisticsRepository;
import com.ceawse.blinkgift.service.CollectionService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {

    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final CollectionAttributeRepository attributeRepository;
    private final CollectionStatisticsRepository statisticsRepository;

    @Override
    public void updateAllCollectionsAttributes() {
        log.info("Start updating attributes...");
        try {
            var collections = indexerClient.getCollections();
            for (var col : collections) {
                processAttributes(col.address);
                Thread.sleep(200); // Rate limiter friendly
            }
        } catch (Exception e) {
            log.error("Error updating attributes cycle", e);
        }
    }

    @Override
    public void updateAllCollectionsStatistics() {
        log.info("Start updating statistics...");
        try {
            var collections = indexerClient.getCollections();
            for (var col : collections) {
                processStatistics(col.address);
                Thread.sleep(200);
            }
        } catch (Exception e) {
            log.error("Error updating statistics cycle", e);
        }
    }

    private void processAttributes(String collectionAddress) {
        try {
            var response = getGemsClient.getAttributes(collectionAddress);
            if (response == null || !response.isSuccess() ||
                    response.getResponse() == null || response.getResponse().getAttributes() == null) {
                return;
            }

            List<CollectionAttributeDocument> documentsToSave = new ArrayList<>();
            Instant now = Instant.now();

            for (var category : response.getResponse().getAttributes()) {
                String traitType = category.getTraitType();
                if (category.getValues() == null) continue;

                for (var val : category.getValues()) {
                    Long priceNano = parseLongSafe(val.getMinPriceNano());
                    BigDecimal price = parseBigDecimalSafe(val.getMinPrice());

                    documentsToSave.add(CollectionAttributeDocument.builder()
                            .id(CollectionAttributeDocument.generateId(collectionAddress, traitType, val.getValue()))
                            .collectionAddress(collectionAddress)
                            .traitType(traitType)
                            .value(val.getValue())
                            .price(price)
                            .priceNano(priceNano)
                            .currency("TON")
                            .itemsCount(val.getCount())
                            .updatedAt(now)
                            .build());
                }
            }

            if (!documentsToSave.isEmpty()) {
                attributeRepository.saveAll(documentsToSave);
                log.debug("Updated {} attributes for {}", documentsToSave.size(), collectionAddress);
            }
        } catch (Exception e) {
            log.error("Failed to parse attributes for {}", collectionAddress, e);
        }
    }

    private void processStatistics(String address) {
        try {
            var dto = getGemsClient.getCollectionStats(address);
            if (dto == null || !dto.isSuccess() || dto.getResponse() == null) return;

            var stats = dto.getResponse();
            Long floorNano = parseLongSafe(stats.getFloorPriceNano());
            BigDecimal floor = null;

            if (floorNano != null) {
                floor = BigDecimal.valueOf(floorNano).divide(BigDecimal.valueOf(1_000_000_000));
            } else if (stats.getFloorPrice() != null) {
                floor = parseBigDecimalSafe(stats.getFloorPrice());
                if (floor != null) {
                    floorNano = floor.multiply(BigDecimal.valueOf(1_000_000_000)).longValue();
                }
            }

            CollectionStatisticsDocument doc = CollectionStatisticsDocument.builder()
                    .collectionAddress(address)
                    .itemsCount(stats.getItemsCount() != null ? stats.getItemsCount() : 0)
                    .ownersCount(stats.getOwnersCount() != null ? stats.getOwnersCount() : 0)
                    .floorPrice(floor)
                    .floorPriceNano(floorNano)
                    .updatedAt(Instant.now())
                    .build();

            statisticsRepository.save(doc);

        } catch (FeignException.NotFound e) {
            log.warn("Collection not found on GetGems: {}", address);
        } catch (Exception e) {
            log.error("Failed to update stats for {}", address, e);
        }
    }

    private Long parseLongSafe(String val) {
        if (val == null) return null;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return null; }
    }

    private BigDecimal parseBigDecimalSafe(String val) {
        if (val == null) return null;
        try { return new BigDecimal(val); } catch (Exception e) { return null; }
    }
}