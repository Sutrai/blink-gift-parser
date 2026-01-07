package com.ceawse.blinkgift.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Всё лишнее (картинки, атрибуты) будет проигнорировано
public class GetGemsSaleItemDto {

    // Идентификаторы
    private String address;
    private String collectionAddress;

    // Минимум метаданных для UI
    private String name;          // "Skull Flower #3709"
    private String kind;          // "OffchainNft" (нужно для флага)

    // Кто продает
    private String ownerAddress;  // Он же seller в контексте листинга

    // Данные о цене
    @JsonProperty("sale")
    private SaleInfo sale;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaleInfo {
        private String fullPrice;       // "9100000000"
        private String currency;        // "TON"
        private String type;            // "FixPriceSale"
        private String contractAddress; // Может понадобиться для проверки типа контракта
    }

    // Хелпер
    public boolean isOffchain() {
        return "OffchainNft".equalsIgnoreCase(kind);
    }
}