package com.ceawse.giftdiscovery.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class MarketAttributeDataDto {
    private BigDecimal price;
    private Integer count;

    public MarketAttributeDataDto(BigDecimal price, Integer count) {
        this.price = price != null ? price : BigDecimal.ZERO;
        this.count = count != null ? count : 0;
    }
}