package com.ceawse.portalsparser.client;

import lombok.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "discoveryInternalClient", url = "http://localhost:7781")
public interface DiscoveryInternalClient {

    // Теперь метод ничего не возвращает (void / 202 Accepted)
    @PostMapping("/internal/v1/enrichment/calculate")
    void enrich(@RequestBody EnrichmentRequest request);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public class EnrichmentRequest {
        private String id;                // ID (адрес) подарка
        private Long timestamp;           // Время события
        private String giftName;
        private String collectionAddress;
        private String model;
        private String backdrop;
        private String symbol;
    }
}