package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.CollectionAttributeDocument;
import com.ceawse.blinkgift.domain.CollectionStatisticsDocument;
import com.ceawse.blinkgift.dto.GetGemsAttributesDto;
import com.ceawse.blinkgift.repository.CollectionAttributeRepository;
import com.ceawse.blinkgift.repository.CollectionStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttributesParserWorker {
    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final CollectionAttributeRepository attributeRepository;
    // Внедряем репозиторий статистики
    private final CollectionStatisticsRepository statisticsRepository;

    @Scheduled(fixedDelay = 60000)
    public void parseAttributes() {
        log.info("Start parsing collection attributes...");
        try {
            List<IndexerApiClient.CollectionDto> collections = indexerClient.getCollections();
            for (IndexerApiClient.CollectionDto collection : collections) {
                processCollection(collection.address);
                Thread.sleep(200);
            }
        } catch (Exception e) {
            log.error("Error during attribute parsing cycle", e);
        }
    }

    private void processCollection(String collectionAddress) {
        try {
            GetGemsAttributesDto response = getGemsClient.getAttributes(collectionAddress);
            if (response == null || !response.isSuccess() || response.getResponse() == null || response.getResponse().getAttributes() == null) {
                return;
            }

            // 1. Получаем сохраненную статистику коллекции (чтобы взять флор)
            Optional<CollectionStatisticsDocument> statsOpt = statisticsRepository.findById(collectionAddress);
            BigDecimal colFloor = statsOpt.map(CollectionStatisticsDocument::getFloorPrice).orElse(null);
            Long colFloorNano = statsOpt.map(CollectionStatisticsDocument::getFloorPriceNano).orElse(null);

            List<CollectionAttributeDocument> documentsToSave = new ArrayList<>();
            Instant now = Instant.now();

            for (GetGemsAttributesDto.AttributeCategoryDto category : response.getResponse().getAttributes()) {
                String traitType = category.getTraitType();
                if (category.getValues() == null) continue;

                for (GetGemsAttributesDto.AttributeValueDto val : category.getValues()) {
                    Long priceNano = val.getMinPriceNano() != null ? Long.parseLong(val.getMinPriceNano()) : null;
                    BigDecimal price = val.getMinPrice() != null ? new BigDecimal(val.getMinPrice()) : null;

                    String docId = CollectionAttributeDocument.generateId(collectionAddress, traitType, val.getValue());

                    CollectionAttributeDocument doc = CollectionAttributeDocument.builder()
                            .id(docId)
                            .collectionAddress(collectionAddress)
                            .traitType(traitType)
                            .value(val.getValue())
                            .price(price)
                            .priceNano(priceNano)
                            .currency("TON")
                            .itemsCount(val.getCount())
                            // Заполняем данными из статистики
                            .collectionFloorPrice(colFloor)
                            .collectionFloorPriceNano(colFloorNano)
                            .updatedAt(now)
                            .build();
                    documentsToSave.add(doc);
                }
            }

            if (!documentsToSave.isEmpty()) {
                attributeRepository.saveAll(documentsToSave);
                log.info("Updated {} attributes for collection {}", documentsToSave.size(), collectionAddress);
            }
        } catch (Exception e) {
            log.error("Failed to parse attributes for collection: " + collectionAddress, e);
        }
    }
}