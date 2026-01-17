package com.ceawse.onchainindexer.dto;

import lombok.*;

public class InternalEnrichmentDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String id;
        private Long timestamp;
        private String giftName;
        private String collectionAddress;

        private Integer serialNumber;
        private Integer totalLimit;

        private String model;
        private String backdrop;
        private String symbol;
    }
}