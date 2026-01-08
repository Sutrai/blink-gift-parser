package com.ceawse.blinkgift.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsCollectionStatsDto {
    private boolean success;
    private ResponseData response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        private String address;
        private String name;
        private Statistics stats;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Statistics {
        private String floorPrice;
        private String floorPriceNano;
        private Long volume;
        private Long itemsCount;
        private Long ownersCount;
    }
}