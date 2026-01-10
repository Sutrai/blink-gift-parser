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
    private String id;           // Адрес подарка (ID в базе)
    private Long timestamp;      // Время события (для firstSeenAt)
    private String giftName;
    private String collectionAddress;
    private String model;
    private String backdrop;
    private String symbol;
}