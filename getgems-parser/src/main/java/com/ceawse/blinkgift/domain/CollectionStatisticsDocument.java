package com.ceawse.blinkgift.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@Document(collection = "collection_statistics")
public class CollectionStatisticsDocument {
    @Id
    private String collectionAddress; // Используем адрес как ID

    private String name;
    private Long itemsCount;
    private Long ownersCount;

    private BigDecimal floorPrice;
    private Long floorPriceNano;

    private BigDecimal volume;

    private Instant updatedAt;
}