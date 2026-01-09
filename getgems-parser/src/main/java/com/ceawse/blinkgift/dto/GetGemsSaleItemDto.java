package com.ceawse.blinkgift.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsSaleItemDto {
    private String address;
    private String collectionAddress;
    private String name;
    private String kind;
    private String ownerAddress;

    @JsonProperty("sale")
    private SaleInfo sale;

    // Добавляем поле attributes
    private List<AttributeDto> attributes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaleInfo {
        private String fullPrice;
        private String currency;
        private String type;
        private String contractAddress;
    }

    // Класс для атрибута
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributeDto {
        private String traitType;
        private String value;
    }

    public boolean isOffchain() {
        return "OffchainNft".equalsIgnoreCase(kind);
    }
}