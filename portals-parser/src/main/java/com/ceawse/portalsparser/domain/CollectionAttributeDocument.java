package com.ceawse.portalsparser.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@Document(collection = "collection_attributes")
@CompoundIndex(name = "col_trait_val_idx", def = "{'collectionAddress': 1, 'traitType': 1, 'value': 1}")
public class CollectionAttributeDocument {

    @Id
    private String id;

    @Indexed
    private String collectionAddress;

    @Indexed
    private String traitType;

    @Indexed
    private String value;

    private BigDecimal price;
    private Long priceNano;
    private String currency;

    private Integer itemsCount;
    private BigDecimal collectionFloorPrice;
    private Long collectionFloorPriceNano;

    private Instant updatedAt;

    public static String generateId(String collectionAddress, String traitType, String value) {
        return collectionAddress + "_" + traitType.replaceAll("\\s+", "_") + "_" + value.replaceAll("\\s+", "_");
    }
}