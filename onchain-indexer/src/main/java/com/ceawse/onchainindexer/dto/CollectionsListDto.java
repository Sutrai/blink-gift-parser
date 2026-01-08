package com.ceawse.onchainindexer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CollectionsListDto {

    private boolean success;
    private ResponseData response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        private String cursor;
        private List<CollectionItemDto> items;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CollectionItemDto {
        private String address;
        private String ownerAddress;
        private String name;
    }
}