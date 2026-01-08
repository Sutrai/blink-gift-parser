package com.ceawse.blinkgift.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGemsAttributesDto {
    private boolean success;
    private ResponseData response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseData {
        private List<AttributeCategoryDto> attributes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributeCategoryDto {
        private String traitType;
        private List<AttributeValueDto> values;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributeValueDto {
        private String value;
        private Integer count;
        private String minPrice;
        private String minPriceNano;
    }
}
