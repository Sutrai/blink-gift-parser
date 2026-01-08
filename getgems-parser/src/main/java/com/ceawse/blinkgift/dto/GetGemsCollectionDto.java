package com.ceawse.blinkgift.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsCollectionDto {
    private boolean success;
    private ResponseData response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        private String address;
        private String name;
        private String description;
        private StatisticsDto statistics;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatisticsDto {
        private String floorPrice;      // Обычно приходит в нано-тонах или строкой
        private String floorPriceNano;  // Явное поле, если есть, иначе парсим floorPrice
        private Long itemsCount;
        private Long ownersCount;
        private String volume;
    }
}