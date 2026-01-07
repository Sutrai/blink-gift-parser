package com.ceawse.coreprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsItemDto {
    private String address;           // Адрес NFT
    private String name;              // Название
    private Long timestamp;           // Время события (ms)
    private String collectionAddress;
    private String lt;                // Логическое время (для сортировки)
    private String hash;
    @JsonProperty("isOffchain")
    private boolean isOffchain;

    private TypeDataDto typeData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TypeDataDto {
        private String type;          // mint, sold, transfer, etc.
        private String price;         // Может быть null
        private String priceNano;     // Точная цена
        private String currency;      // TON, USD
        private String oldOwner;
        private String newOwner;
    }
}