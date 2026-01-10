package com.ceawse.onchainindexer.dto;

import lombok.*;

public class InternalEnrichmentDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String id;           // Добавили
        private Long timestamp;      // Добавили
        private String giftName;
        private String collectionAddress;
        private String model;
        private String backdrop;
        private String symbol;
    }
}