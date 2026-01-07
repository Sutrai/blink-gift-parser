package com.ceawse.coreprocessor.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@Document(collection = "current_sales")
public class CurrentSaleDocument {

    @Id
    private String id; // Mongo ID

    @Indexed(unique = true) // Один предмет - одна активная продажа
    private String address;

    @Indexed
    private String collectionAddress;

    private String name;

    // Храним и строку (для UI) и число (для сортировки)
    private String price;

    @Indexed // Индекс для сортировки "Сначала дешевые"
    private Long priceNano;

    private String currency;

    private String seller; // address владельца

    private Instant listedAt;
    private Instant updatedAt;

    private boolean isOffchain;
}