package com.ceawse.onchainindexer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CollectionHistoryDto {

    private boolean success;
    private ResponseData response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        private String cursor;
        private List<HistoryItemDto> items;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoryItemDto {
        private String address;           // Адрес самого подарка (NFT)
        private String name;              // "Dog #123"
        private Long timestamp;           // Время события (mint)
        private String collectionAddress;
        private TypeDataDto typeData;     // Чтобы проверить тип события
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TypeDataDto {
        private String type; // "mint", "transfer" и т.д.
    }
}