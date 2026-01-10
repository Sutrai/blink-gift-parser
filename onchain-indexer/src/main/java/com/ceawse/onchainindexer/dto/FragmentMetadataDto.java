package com.ceawse.onchainindexer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FragmentMetadataDto {
    private List<AttributeDto> attributes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributeDto {
        private String trait_type;
        private String value;
    }
}