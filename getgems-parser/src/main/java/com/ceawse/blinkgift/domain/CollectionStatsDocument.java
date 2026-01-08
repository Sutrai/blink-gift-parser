package com.ceawse.blinkgift.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Builder
@Document(collection = "collection_stats")
public class CollectionStatsDocument {
    @Id
    private String collectionAddress; // ID документа = адрес коллекции
    private String name;
    private Long floorPriceNano;
    private String floorPrice; // Для удобства чтения
    private Long volume;
    private Instant updatedAt;
}