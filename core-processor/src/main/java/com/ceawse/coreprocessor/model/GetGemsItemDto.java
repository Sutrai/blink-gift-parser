package com.ceawse.coreprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsItemDto {
    private String address;
    private String name;
    private Long timestamp;
    private String collectionAddress;
    private String lt;
    private String hash;
    @JsonProperty("isOffchain")
    private boolean isOffchain;

    private TypeDataDto typeData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TypeDataDto {
        private String type;
        private String price;
        private String priceNano;
        private String currency;
        private String oldOwner;
        private String newOwner;
    }
}