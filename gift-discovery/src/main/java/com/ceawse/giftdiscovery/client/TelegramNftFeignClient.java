package com.ceawse.giftdiscovery.client;

import com.ceawse.giftdiscovery.config.GetGemsProxyConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "telegramNftClient",
        url = "https://t.me",
        configuration = GetGemsProxyConfig.class
)
public interface TelegramNftFeignClient {

    // Возвращает HTML страницу подарка
    @GetMapping("/nft/{slug}")
    String getNftPage(@PathVariable("slug") String slug);
}