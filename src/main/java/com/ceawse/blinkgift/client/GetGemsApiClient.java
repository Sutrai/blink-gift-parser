package com.ceawse.blinkgift.client;

import com.ceawse.blinkgift.config.GetGemsProxyConfig;
import com.ceawse.blinkgift.dto.GetGemsHistoryDto;
import com.ceawse.blinkgift.dto.GetGemsSalePageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "getGemsClient",
        url = "https://api.getgems.io/public-api",
        configuration = GetGemsProxyConfig.class
)
public interface GetGemsApiClient {

    @GetMapping("/v1/nfts/history/gifts")
    GetGemsHistoryDto getHistory(
            @RequestParam("minTime") Long minTime,
            @RequestParam(value = "maxTime", required = false) Long maxTime,
            @RequestParam("limit") int limit,
            @RequestParam(value = "after", required = false) String cursor,
            @RequestParam("types") List<String> types,
            @RequestParam("reverse") boolean reverse
    );

    @GetMapping("/v1/nfts/on-sale/{collectionAddress}")
    GetGemsSalePageDto getOnSale(
            @PathVariable("collectionAddress") String collectionAddress,
            @RequestParam("limit") int limit,
            @RequestParam(value = "after", required = false) String cursor
    );
}