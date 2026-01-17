package com.ceawse.giftdiscovery.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichmentRequest {
    private String id;
    private String giftName;
    private String collectionAddress;
    private Long timestamp;

    private Integer serialNumber;
    private Integer totalLimit;

    private String model;
    private String backdrop;
    private String symbol;
}