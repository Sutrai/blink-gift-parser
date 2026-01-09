package com.ceawse.giftdiscovery.model.read;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;

@Data
@Document(collection = "collection_attributes")
public class CollectionAttributeDocument {
    @Id
    private String id;
    private String collectionAddress;
    private String traitType;
    private String value;
    private BigDecimal price;
    private Integer itemsCount;

    public static String generateId(String collectionAddress, String traitType, String value) {
        return collectionAddress + "_" + traitType.replaceAll("\\s+", "_") + "_" + value.replaceAll("\\s+", "_");
    }
}