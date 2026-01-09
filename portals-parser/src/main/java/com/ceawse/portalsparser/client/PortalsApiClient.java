package com.ceawse.portalsparser.client;

import com.ceawse.portalsparser.config.PortalsProxyConfig;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsSearchResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "portalsClient",
        url = "${portals.api-url}",
        configuration = PortalsProxyConfig.class
)
public interface PortalsApiClient {

    // Эндпоинт для получения событий (Realtime)
    // Аналог marketActivity из Python
    @GetMapping("/market/actions/")
    PortalsActionsResponseDto getMarketActivity(
            @RequestParam("offset") int offset,
            @RequestParam("limit") int limit,
            @RequestParam("sort_by") String sortBy, // usually "latest" -> listed_at desc
            @RequestParam("action_types") String actionTypes // buy,listing,price_update
    );

    // Эндпоинт для получения текущих листингов (Snapshot)
    // Аналог search из Python
    @GetMapping("/nfts/search")
    PortalsSearchResponseDto searchNfts(
            @RequestParam("offset") int offset,
            @RequestParam("limit") int limit,
            @RequestParam("sort_by") String sortBy, // usually "price+asc"
            @RequestParam("status") String status, // "listed"
            @RequestParam("exclude_bundled") boolean excludeBundled
    );
}