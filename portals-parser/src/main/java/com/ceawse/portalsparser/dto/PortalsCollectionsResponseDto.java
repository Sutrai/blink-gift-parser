package com.ceawse.portalsparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalsCollectionsResponseDto {
    private List<CollectionInfo> collections;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CollectionInfo {
        private String id;
        private String name;
        @JsonProperty("short_name")
        private String shortName;
        @JsonProperty("floor_price")
        private String floorPrice;
        private Integer supply;
        @JsonProperty("listed_count")
        private Integer listedCount;
    }
}