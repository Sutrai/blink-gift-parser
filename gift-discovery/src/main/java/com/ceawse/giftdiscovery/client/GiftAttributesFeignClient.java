package com.ceawse.giftdiscovery.client;

import com.ceawse.giftdiscovery.dto.GiftAttributesDto;
import com.ceawse.giftdiscovery.config.GetGemsProxyConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "giftAttributesClient",
        url = "https://nft.fragment.com",
        configuration = GetGemsProxyConfig.class
)
public interface GiftAttributesFeignClient {

    // Получаем атрибуты одного подарка по имени
    @GetMapping("/gift/{name}.json")
    GiftAttributesDto getAttributes(@PathVariable("name") String name);

    @GetMapping("/gifts/batch")
    List<GiftAttributesDto> getAttributesBatch(@PathVariable("names") List<String> names);
}
