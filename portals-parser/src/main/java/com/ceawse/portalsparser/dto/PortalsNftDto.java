package com.ceawse.portalsparser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortalsNftDto {
    private String id;

    @JsonProperty("external_collection_number")
    private String tgId;

    @JsonProperty("collection_id")
    private String collectionId;

    private String name;

    private String price;

    private String status;
}