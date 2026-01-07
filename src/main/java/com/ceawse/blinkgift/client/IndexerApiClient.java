package com.ceawse.blinkgift.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@FeignClient(name = "indexerClient", url = "http://localhost:7780")
public interface IndexerApiClient {
    @GetMapping("/internal/v1/collections")
    List<CollectionDto> getCollections();

    class CollectionDto {
        public String address;
    }
}