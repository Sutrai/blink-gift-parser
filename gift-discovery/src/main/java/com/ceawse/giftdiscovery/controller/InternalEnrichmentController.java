package com.ceawse.giftdiscovery.controller;

import com.ceawse.giftdiscovery.service.MarketDataService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalEnrichmentController {

    private final MarketDataService marketDataService;

    @PostMapping("/internal/v1/enrichment/calculate")
    public EnrichmentResponse enrich(@RequestBody EnrichmentRequest request) {
        log.debug("Internal enrichment request for gift: {}", request.getGiftName());

        // 1. Определяем реальный адрес коллекции
        String colAddr = marketDataService.resolveCollectionAddress(request.getGiftName(), request.getCollectionAddress());

        // 2. Берем текущий флор (с защитой от null)
        BigDecimal floor = marketDataService.getCollectionFloor(colAddr);
        if (floor == null) floor = BigDecimal.ZERO;

        // 3. Берем данные по атрибутам
        var modelData = marketDataService.getAttributeData(colAddr, "Model", request.getModel());
        var backdropData = marketDataService.getAttributeData(colAddr, "Backdrop", request.getBackdrop());
        var symbolData = marketDataService.getAttributeData(colAddr, "Symbol", request.getSymbol());

        // 4. Считаем рыночную оценку
        BigDecimal modelP = (modelData != null && modelData.getPrice() != null) ? modelData.getPrice() : floor;
        BigDecimal backdropP = (backdropData != null && backdropData.getPrice() != null) ? backdropData.getPrice() : floor;

        BigDecimal estimated = floor.multiply(BigDecimal.valueOf(2))
                .add(modelP)
                .add(backdropP)
                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);

        return EnrichmentResponse.builder()
                .resolvedCollectionAddress(colAddr)
                .collectionFloorPrice(floor)
                .estimatedPrice(estimated)
                .modelPrice(modelData != null ? modelData.getPrice() : null)
                .modelCount(modelData != null ? modelData.getCount() : null)
                .backdropPrice(backdropData != null ? backdropData.getPrice() : null)
                .backdropCount(backdropData != null ? backdropData.getCount() : null)
                .symbolPrice(symbolData != null ? symbolData.getPrice() : null)
                .symbolCount(symbolData != null ? symbolData.getCount() : null)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrichmentRequest {
        private String giftName;
        private String collectionAddress;
        private String model;
        private String backdrop;
        private String symbol;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrichmentResponse {
        private String resolvedCollectionAddress;
        private BigDecimal collectionFloorPrice;
        private BigDecimal estimatedPrice;
        private BigDecimal modelPrice;
        private Integer modelCount;
        private BigDecimal backdropPrice;
        private Integer backdropCount;
        private BigDecimal symbolPrice;
        private Integer symbolCount;
    }
}