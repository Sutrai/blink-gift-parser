package com.ceawse.portalsparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalsNftDto {
    private String id;

    @JsonProperty("external_collection_number")
    private Long externalCollectionNumber;

    @JsonProperty("collection_id")
    private String collectionId;

    private String name;
    private String price;
    private String status;

    private List<AttributeDto> attributes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributeDto {
        private String type;
        private String value;
    }
}