package com.ceawse.coreprocessor.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@Document(collection = "sold_gifts")
public class SoldGiftDocument {

    @Id
    private String id;

    @Indexed
    private String address;

    private String collectionAddress;
    private String name;

    private String price;
    private Long priceNano;
    private String currency;

    private String seller;
    private String buyer;

    private Instant soldAt;

    private boolean isOffchain;
}