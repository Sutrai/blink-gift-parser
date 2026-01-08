package com.ceawse.giftdiscovery.dto;

import lombok.Data;

import java.util.List;

@Data
public class GiftAttributesDto {
    private String lottie;
    private List<AttributeDto> attributes;

    @Data
    public static class AttributeDto {
        private String trait_type;
        private String value;
    }
}
