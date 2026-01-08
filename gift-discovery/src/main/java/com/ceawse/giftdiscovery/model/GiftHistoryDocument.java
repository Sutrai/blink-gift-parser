package com.ceawse.giftdiscovery.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "gift_history")
public class GiftHistoryDocument {
    @Id
    private String id;

    @Indexed
    private String collectionAddress;

    @Indexed
    private String address; // Identifier/Address

    private String name;

    @Indexed
    private Long timestamp; // Время события

    private Boolean isOffchain;

    // Остальные поля (цена, владелец) для Discovery не критичны, можно не мапить
}