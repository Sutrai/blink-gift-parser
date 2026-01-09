package com.ceawse.giftdiscovery.model.read;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;

@Data
@Document(collection = "collection_statistics")
public class CollectionStatisticsDocument {
    @Id
    private String collectionAddress;
    private BigDecimal floorPrice;
}