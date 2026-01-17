package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.dto.MarketAttributeDataDto;
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

    @Async("virtualThreadExecutor")
    public void processEnrichmentAsync(EnrichmentRequest request) {
        try {
            String colAddr = marketDataService.resolveCollectionAddress(request.getGiftName(), request.getCollectionAddress());
            BigDecimal colFloor = marketDataService.getCollectionFloor(colAddr);

            // Получаем данные по атрибутам
            var mData = marketDataService.getAttributeData(colAddr, "Model", request.getModel());
            var bData = marketDataService.getAttributeData(colAddr, "Backdrop", request.getBackdrop());
            var sData = marketDataService.getAttributeData(colAddr, "Symbol", request.getSymbol());

            // Собираем детализированные объекты атрибутов
            var parameters = UniqueGiftDocument.GiftParameters.builder()
                    .model(buildDetail(request.getModel(), mData, request.getTotalLimit()))
                    .backdrop(buildDetail(request.getBackdrop(), bData, request.getTotalLimit()))
                    .symbol(buildDetail(request.getSymbol(), sData, request.getTotalLimit()))
                    .build();

            // Расчет оценки (упрощенная формула)
            BigDecimal estPrice = calculateEstimatedPrice(colFloor, parameters);

            UniqueGiftDocument gift = UniqueGiftDocument.builder()
                    .id(request.getId())
                    .name(request.getGiftName())
                    .serialNumber(request.getSerialNumber())
                    .totalLimit(request.getTotalLimit())
                    .collectionAddress(colAddr)
                    .isOffchain(false)
                    .firstSeenAt(Instant.ofEpochMilli(request.getTimestamp()))
                    .lastSeenAt(Instant.now())
                    .parameters(parameters)
                    .marketData(UniqueGiftDocument.MarketData.builder()
                            .collectionFloorPrice(colFloor)
                            .estimatedPrice(estPrice)
                            .priceUpdatedAt(Instant.now())
                            .build())
                    .build();

            uniqueGiftRepository.save(gift);
            log.info("Enriched gift saved: {}", request.getGiftName());

        } catch (Exception e) {
            log.error("Enrichment failed for: {}", request.getGiftName(), e);
        }
    }

    private UniqueGiftDocument.AttributeDetail buildDetail(String value, MarketAttributeDataDto data, Integer total) {
        if (value == null) return null;

        Integer count = (data != null) ? data.getCount() : 0;
        // Считаем процент редкости: (количество / всего в коллекции) * 100
        Double percent = (total != null && total > 0) ? (count.doubleValue() / total.doubleValue()) * 100 : 0.0;

        return UniqueGiftDocument.AttributeDetail.builder()
                .value(value)
                .floorPrice((data != null) ? data.getPrice() : null)
                .rarityCount(count)
                .rarityPercent(BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP).doubleValue())
                .build();
    }

    private BigDecimal calculateEstimatedPrice(BigDecimal floor, UniqueGiftDocument.GiftParameters params) {
        if (floor == null) return BigDecimal.ZERO;

        BigDecimal mPrice = (params.getModel().getFloorPrice() != null) ? params.getModel().getFloorPrice() : floor;
        BigDecimal bPrice = (params.getBackdrop().getFloorPrice() != null) ? params.getBackdrop().getFloorPrice() : floor;

        return floor.multiply(BigDecimal.valueOf(2)).add(mPrice).add(bPrice)
                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
    }
}