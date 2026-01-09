package com.ceawse.portalsparser.client;

import lombok.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "discoveryInternalClient", url = "http://localhost:7781")
public interface DiscoveryInternalClient {

    @PostMapping("/internal/v1/enrichment/calculate")
    EnrichmentResponse enrich(@RequestBody EnrichmentRequest request);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class EnrichmentRequest {
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
    class EnrichmentResponse {
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