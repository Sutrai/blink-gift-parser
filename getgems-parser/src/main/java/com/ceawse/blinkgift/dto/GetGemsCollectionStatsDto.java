package com.ceawse.blinkgift.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsCollectionStatsDto {
    private boolean success;
    private ResponseData response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {

        private String floorPrice;
        private String floorPriceNano;

        @JsonProperty("totalVolumeSold")
        private String volume;

        private Long itemsCount;

        @JsonProperty("holders")
        private Long ownersCount;
    }
}