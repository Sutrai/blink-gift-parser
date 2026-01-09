package com.ceawse.giftdiscovery.dto.internal;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EnrichmentResponse {
    private String resolvedCollectionAddress;
    private BigDecimal collectionFloorPrice;
    private BigDecimal estimatedPrice;

    // Цены и редкость для каждого атрибута
    private BigDecimal modelPrice;
    private Integer modelCount;
    private BigDecimal backdropPrice;
    private Integer backdropCount;
    private BigDecimal symbolPrice;
    private Integer symbolCount;
}
