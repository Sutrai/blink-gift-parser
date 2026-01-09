package com.ceawse.giftdiscovery.dto.internal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnrichmentRequest {
    private String giftName;
    private String collectionAddress;
    private String model;
    private String backdrop;
    private String symbol;
}