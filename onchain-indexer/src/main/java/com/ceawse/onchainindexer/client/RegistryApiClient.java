package com.ceawse.onchainindexer.client;

import com.ceawse.onchainindexer.config.GetGemsProxyConfig;
import com.ceawse.onchainindexer.dto.CollectionHistoryDto;
import com.ceawse.onchainindexer.dto.CollectionsListDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@FeignClient(
        name = "registryClient",
        url = "https://api.getgems.io/public-api",
        configuration = GetGemsProxyConfig.class
)
public interface RegistryApiClient {

    @GetMapping("/v1/gifts/collections")
    CollectionsListDto getCollections(
            @RequestParam("limit") int limit,
            @RequestParam(value = "after", required = false) String cursor
    );

    @GetMapping("/v1/collection/history/{address}")
    CollectionHistoryDto getCollectionHistory(
            @PathVariable("address") String address,
            @RequestParam("limit") int limit,
            @RequestParam(value = "after", required = false) String cursor,
            @RequestParam("types") List<String> types,
            @RequestParam("reverse") boolean reverse
    );
}