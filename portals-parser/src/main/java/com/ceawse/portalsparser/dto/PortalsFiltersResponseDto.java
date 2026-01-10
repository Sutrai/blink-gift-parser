package com.ceawse.portalsparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalsFiltersResponseDto {
    @JsonProperty("floor_prices")
    private Map<String, CollectionFilters> floorPrices;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CollectionFilters {
        private Map<String, String> models;
        private Map<String, String> symbols;
        private Map<String, String> backdrops;
    }
}