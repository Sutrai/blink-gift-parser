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
    private String id;
    private String name;

    private Integer serialNumber;
    private Integer totalLimit;

    private String collectionAddress;
    private Boolean isOffchain;

    private Instant firstSeenAt;
    private Instant lastSeenAt;

    // Структурированные параметры (как в твоем примере)
    private GiftParameters parameters;

    private MarketData marketData;

    @Data
    @Builder
    public static class GiftParameters {
        private AttributeDetail model;
        private AttributeDetail backdrop;
        private AttributeDetail symbol;
    }

    @Data
    @Builder
    public static class AttributeDetail {
        private String value;
        private BigDecimal floorPrice;
        private Integer rarityCount;
        private Double rarityPercent; // Процент редкости
    }

    @Data
    @Builder
    public static class MarketData {
        private BigDecimal collectionFloorPrice;
        private BigDecimal estimatedPrice;
        private Instant priceUpdatedAt;
    }
}