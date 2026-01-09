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

    private boolean isOffchain;
    private String discoverySource;
    private Instant firstSeenAt;
    private Instant lastSeenAt;

    private GiftAttributes attributes;
    private MarketData marketData;

    @Data
    @Builder
    public static class GiftAttributes {
        private String model;
        private BigDecimal modelPrice;
        private Integer modelRarityCount; // Количество предметов с таким атрибутом

        private String backdrop;
        private BigDecimal backdropPrice;
        private Integer backdropRarityCount;

        private String symbol;
        private BigDecimal symbolPrice;
        private Integer symbolRarityCount;

        private Instant updatedAt;
    }

    @Data
    @Builder
    public static class MarketData {
        private BigDecimal collectionFloorPrice; // Floor коллекции
        private BigDecimal estimatedPrice;       // Расчетная цена по формуле
        private Instant priceUpdatedAt;
    }
}