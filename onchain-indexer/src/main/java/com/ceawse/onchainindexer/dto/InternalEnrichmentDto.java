package com.ceawse.onchainindexer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;

public class InternalEnrichmentDto {

    @Data
    @Builder
    public static class Request {
        private String giftName;
        private String collectionAddress;
        private String model;
        private String backdrop;
        private String symbol;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
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