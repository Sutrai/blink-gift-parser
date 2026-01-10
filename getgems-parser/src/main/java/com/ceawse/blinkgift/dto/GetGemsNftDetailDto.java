package com.ceawse.blinkgift.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsNftDetailDto {
    private boolean success;
    private ResponseData response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        private String address;
        private String name;
        private String collectionAddress;
        private List<AttributeDto> attributes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributeDto {
        private String traitType;
        private String value;
    }
}