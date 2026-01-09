package com.ceawse.portalsparser.domain;

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
    private String id;

    @Indexed
    private String name;

    @Indexed
    private String collectionAddress;

    private GiftAttributes attributes;

    private MarketData marketData;

    private Instant lastSeenAt;

    @Data
    @Builder
    public static class GiftAttributes {
        private String model;
        private BigDecimal modelPrice;
        private Integer modelRarityCount;

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
        private BigDecimal collectionFloorPrice;
        private BigDecimal estimatedPrice;
        private Instant priceUpdatedAt;
    }
}