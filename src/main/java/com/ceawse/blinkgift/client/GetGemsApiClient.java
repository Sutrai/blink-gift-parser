package com.ceawse.blinkgift.client;

import com.ceawse.blinkgift.config.GetGemsProxyConfig;
import com.ceawse.blinkgift.dto.GetGemsHistoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "getGemsClient",
        url = "https://api.getgems.io/public-api",
        configuration = GetGemsProxyConfig.class
)
public interface GetGemsApiClient {

    /**
     * Получение глобальной истории подарков.
     * Не используем reverse=true для продакшена.
     */
    @GetMapping("/v1/nfts/history/gifts")
    GetGemsHistoryDto getHistory(
            @RequestParam("minTime") Long minTime, // start window
            @RequestParam(value = "maxTime", required = false) Long maxTime, // end window
            @RequestParam("limit") int limit,
            @RequestParam(value = "after", required = false) String cursor
    );
}