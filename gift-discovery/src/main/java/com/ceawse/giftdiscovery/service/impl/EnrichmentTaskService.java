package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.dto.internal.EnrichmentRequest;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import com.ceawse.giftdiscovery.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentTaskService {

    private final MarketDataService marketDataService;
    private final UniqueGiftRepository uniqueGiftRepository;

    @Async("virtualThreadExecutor") // Используем виртуальные потоки из твоего AsyncConfig
    public void processEnrichmentAsync(EnrichmentRequest request) {
        try {
            log.debug("Starting async enrichment for: {}", request.getGiftName());

            // 1. Определяем реальный адрес коллекции
            String colAddr = marketDataService.resolveCollectionAddress(request.getGiftName(), request.getCollectionAddress());

            // 2. Получаем рыночные данные (флор и атрибуты)
            BigDecimal floor = marketDataService.getCollectionFloor(colAddr);
            if (floor == null) floor = BigDecimal.ZERO;

            var modelData = marketDataService.getAttributeData(colAddr, "Model", request.getModel());
            var backdropData = marketDataService.getAttributeData(colAddr, "Backdrop", request.getBackdrop());
            var symbolData = marketDataService.getAttributeData(colAddr, "Symbol", request.getSymbol());

            // 3. Считаем оценку
            BigDecimal modelP = (modelData != null && modelData.getPrice() != null) ? modelData.getPrice() : floor;
            BigDecimal backdropP = (backdropData != null && backdropData.getPrice() != null) ? backdropData.getPrice() : floor;

            BigDecimal estimated = floor.multiply(BigDecimal.valueOf(2))
                    .add(modelP)
                    .add(backdropP)
                    .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);

            // 4. Формируем документ UniqueGiftDocument
            UniqueGiftDocument gift = UniqueGiftDocument.builder()
                    .id(request.getId())
                    .name(request.getGiftName())
                    .collectionAddress(colAddr)
                    .isOffchain(false)
                    .discoverySource("ONCHAIN_INDEXER")
                    .firstSeenAt(request.getTimestamp() != null ? Instant.ofEpochMilli(request.getTimestamp()) : Instant.now())
                    .lastSeenAt(Instant.now())
                    .attributes(UniqueGiftDocument.GiftAttributes.builder()
                            .model(request.getModel())
                            .modelPrice(modelData != null ? modelData.getPrice() : null)
                            .modelRarityCount(modelData != null ? modelData.getCount() : null)
                            .backdrop(request.getBackdrop())
                            .backdropPrice(backdropData != null ? backdropData.getPrice() : null)
                            .backdropRarityCount(backdropData != null ? backdropData.getCount() : null)
                            .symbol(request.getSymbol())
                            .symbolPrice(symbolData != null ? symbolData.getPrice() : null)
                            .symbolRarityCount(symbolData != null ? symbolData.getCount() : null)
                            .updatedAt(Instant.now())
                            .build())
                    .marketData(UniqueGiftDocument.MarketData.builder()
                            .collectionFloorPrice(floor)
                            .estimatedPrice(estimated)
                            .priceUpdatedAt(Instant.now())
                            .build())
                    .build();

            // 5. Сохраняем в MongoDB
            uniqueGiftRepository.save(gift);
            log.info("Successfully enriched and saved gift: {}", request.getGiftName());

        } catch (Exception e) {
            log.error("Failed to process async enrichment for gift: {}", request.getGiftName(), e);
        }
    }
}