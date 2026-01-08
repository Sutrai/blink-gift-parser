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
        private String address;
        private String name;
        private Long timestamp;
        private String collectionAddress;
        private TypeDataDto typeData;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TypeDataDto {
        private String type;
    }
}