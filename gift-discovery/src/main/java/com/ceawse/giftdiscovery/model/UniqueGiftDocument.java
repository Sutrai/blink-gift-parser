package com.ceawse.giftdiscovery.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@Document(collection = "unique_gifts")
public class UniqueGiftDocument {

    @Id
    private String id; // Address или Hash

    @Indexed
    private String name;

    @Indexed
    private String collectionAddress;

    @Indexed
    private boolean isOffchain;

    private String discoverySource; // 'REGISTRY' или 'HISTORY'

    private Instant firstSeenAt;
    private Instant lastSeenAt;

    // --- Заготовки на будущее (Service 2 и 3) ---
    private GiftAttributes attributes;
    private MarketData marketData;

    @Data
    @Builder
    public static class GiftAttributes {
        private String model;
        private String backdrop;
        private String symbol;
        private Instant updatedAt;
    }

    @Data
    @Builder
    public static class MarketData {
        private BigDecimal floorPrice;
        private BigDecimal avgPrice;
        private Instant priceUpdatedAt;
    }
}