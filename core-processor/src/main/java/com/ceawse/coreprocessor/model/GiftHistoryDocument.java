package com.ceawse.coreprocessor.model;

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
    private String address;
    @Indexed
    private String hash; // Для snapshot-событий можно генерировать уникальный хеш: snapshotId + address

    private String lt;
    private String name;
    private Long timestamp;

    // Типы: putupforsale, sold, cancelsale, ...
    // НОВЫЕ ТИПЫ: SNAPSHOT_LIST, SNAPSHOT_FINISH, SNAPSHOT_UNLIST
    private String eventType;

    private Boolean isOffchain;
    private String price;
    private String priceNano;
    private String currency;
    private String oldOwner;
    private String newOwner;

    // --- НОВОЕ ПОЛЕ ---
    @Indexed
    private String snapshotId;
    private String eventPayload;
}