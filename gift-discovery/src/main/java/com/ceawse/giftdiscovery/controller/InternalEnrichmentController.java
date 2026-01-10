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

        if (request == null || request.getGiftName() == null) {
            log.error("Received empty enrichment request");
            return new EnrichmentResponse();
        }

        log.info("=== ENRICHMENT REQUEST STARTED ===");
        log.info("Gift Name: {}", request.getGiftName());
        log.info("Provided Collection Address: {}", request.getCollectionAddress());
        log.info("Model: {}", request.getModel());
        log.info("Backdrop: {}", request.getBackdrop());
        log.info("Symbol: {}", request.getSymbol());
        log.info("Full Request: {}", request);

        try {
            // 1. Определяем реальный адрес коллекции
            String colAddr = marketDataService.resolveCollectionAddress(request.getGiftName(), request.getCollectionAddress());
            log.info("Resolved Collection Address: {}", colAddr);

            // 2. Берем текущий флор (с защитой от null)
            BigDecimal floor = marketDataService.getCollectionFloor(colAddr);
            if (floor == null || floor.compareTo(BigDecimal.ZERO) <= 0) {
                floor = BigDecimal.ZERO;
            }
            log.info("Collection Floor Price: {}", floor);

            // 3. Берем данные по атрибутам
            log.debug("Fetching attribute data...");
            var modelData = marketDataService.getAttributeData(colAddr, "Model", request.getModel());
            var backdropData = marketDataService.getAttributeData(colAddr, "Backdrop", request.getBackdrop());
            var symbolData = marketDataService.getAttributeData(colAddr, "Symbol", request.getSymbol());

            log.debug("Model Data: price={}, count={}",
                    modelData != null ? modelData.getPrice() : "null",
                    modelData != null ? modelData.getCount() : "null");
            log.debug("Backdrop Data: price={}, count={}",
                    backdropData != null ? backdropData.getPrice() : "null",
                    backdropData != null ? backdropData.getCount() : "null");
            log.debug("Symbol Data: price={}, count={}",
                    symbolData != null ? symbolData.getPrice() : "null",
                    symbolData != null ? symbolData.getCount() : "null");

            // 4. Считаем рыночную оценку
            BigDecimal modelP = (modelData != null && modelData.getPrice() != null) ? modelData.getPrice() : floor;
            BigDecimal backdropP = (backdropData != null && backdropData.getPrice() != null) ? backdropData.getPrice() : floor;

            BigDecimal estimated = floor.multiply(BigDecimal.valueOf(2))
                    .add(modelP)
                    .add(backdropP)
                    .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);

            log.info("Calculation: (floor={} * 2 + model={} + backdrop={}) / 4 = estimated={}",
                    floor, modelP, backdropP, estimated);

            EnrichmentResponse response = EnrichmentResponse.builder()
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

            log.info("=== ENRICHMENT RESPONSE ===");
            log.info("Estimated Price: {}", response.getEstimatedPrice());
            log.info("Collection Address: {}", response.getResolvedCollectionAddress());
            log.info("Model Price/Count: {}/{}", response.getModelPrice(), response.getModelCount());
            log.info("Backdrop Price/Count: {}/{}", response.getBackdropPrice(), response.getBackdropCount());
            log.info("Symbol Price/Count: {}/{}", response.getSymbolPrice(), response.getSymbolCount());
            log.info("=== ENRICHMENT COMPLETED SUCCESSFULLY ===\n");

            return response;

        } catch (Exception e) {
            log.error("=== ENRICHMENT FAILED ===");
            log.error("Error processing gift: {}", request.getGiftName(), e);
            log.error("=== ENRICHMENT ABORTED ===\n");
            throw e;
        }
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

        @Override
        public String toString() {
            return String.format(
                    "EnrichmentRequest{giftName='%s', collectionAddress='%s', model='%s', backdrop='%s', symbol='%s'}",
                    giftName,
                    collectionAddress != null ? collectionAddress.substring(0, Math.min(10, collectionAddress.length())) + "..." : "null",
                    model,
                    backdrop,
                    symbol
            );
        }
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

        @Override
        public String toString() {
            return String.format(
                    "EnrichmentResponse{collection='%s', floor=%s, estimated=%s, model=%s/%s, backdrop=%s/%s, symbol=%s/%s}",
                    resolvedCollectionAddress != null ? resolvedCollectionAddress.substring(0, Math.min(10, resolvedCollectionAddress.length())) + "..." : "null",
                    collectionFloorPrice,
                    estimatedPrice,
                    modelPrice, modelCount,
                    backdropPrice, backdropCount,
                    symbolPrice, symbolCount
            );
        }
    }
}