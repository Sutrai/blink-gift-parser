package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.CollectionAttributeDocument;
import com.ceawse.blinkgift.dto.GetGemsAttributesDto;
import com.ceawse.blinkgift.repository.CollectionAttributeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttributesParserWorker {

    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final CollectionAttributeRepository attributeRepository;

    // Запускаем раз в минуту (или реже, в зависимости от лимитов API)
    @Scheduled(fixedDelay = 60000)
    public void parseAttributes() {
        log.info("Start parsing collection attributes...");
        try {
            // 1. Получаем список коллекций от нашего индексера
            List<IndexerApiClient.CollectionDto> collections = indexerClient.getCollections();

            for (IndexerApiClient.CollectionDto collection : collections) {
                processCollection(collection.address);
                // Небольшая пауза, чтобы не заспамить API GetGems
                Thread.sleep(200);
            }

        } catch (Exception e) {
            log.error("Error during attribute parsing cycle", e);
        }
    }

    private void processCollection(String collectionAddress) {
        try {
            // 2. Запрашиваем атрибуты у GetGems
            GetGemsAttributesDto response = getGemsClient.getAttributes(collectionAddress);

            if (response == null || !response.isSuccess() || response.getResponse() == null || response.getResponse().getAttributes() == null) {
                return;
            }

            List<CollectionAttributeDocument> documentsToSave = new ArrayList<>();
            Instant now = Instant.now();

            // 3. Маппим ответ в БД документы
            for (GetGemsAttributesDto.AttributeCategoryDto category : response.getResponse().getAttributes()) {
                String traitType = category.getTraitType();

                // Фильтруем только нужные категории, если нужно, или сохраняем всё
                // if (!List.of("Backdrop", "Model", "Symbol").contains(traitType)) continue;

                if (category.getValues() == null) continue;

                for (GetGemsAttributesDto.AttributeValueDto val : category.getValues()) {
                    // Если цены нет, можно пропускать или сохранять с null (значит флора нет)
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
                            .currency("TON") // API не возвращает валюту здесь, по умолчанию TON
                            .itemsCount(val.getCount())
                            .updatedAt(now)
                            .build();

                    documentsToSave.add(doc);
                }
            }

            // 4. Сохраняем (Upsert)
            if (!documentsToSave.isEmpty()) {
                attributeRepository.saveAll(documentsToSave);
                log.info("Updated {} attributes for collection {}", documentsToSave.size(), collectionAddress);
            }

        } catch (Exception e) {
            log.error("Failed to parse attributes for collection: " + collectionAddress, e);
        }
    }
}