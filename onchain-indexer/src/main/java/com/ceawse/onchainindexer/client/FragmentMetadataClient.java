package com.ceawse.onchainindexer.client;

import com.ceawse.onchainindexer.config.GetGemsProxyConfig;
import com.ceawse.onchainindexer.dto.FragmentMetadataDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "fragmentClient", url = "https://nft.fragment.com", configuration = GetGemsProxyConfig.class)
public interface FragmentMetadataClient {
    @GetMapping("/gift/{slug}.json")
    FragmentMetadataDto getMetadata(@PathVariable("slug") String slug);
}