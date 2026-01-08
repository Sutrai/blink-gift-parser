package com.ceawse.blinkgift.domain;

import com.ceawse.blinkgift.dto.GetGemsItemDto;
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

    @Indexed(unique = true)
    private String hash;

    private String lt;
    private String name;
    private Long timestamp;
    private String eventType; // mint, sold, transfer, SNAPSHOT_LIST...

    private Boolean isOffchain;

    // Детали сделки
    private String price;
    private String priceNano;
    private String currency;
    private String oldOwner;
    private String newOwner;

    // --- ДОБАВЛЕННОЕ ПОЛЕ ---
    @Indexed
    private String snapshotId;
    private String eventPayload;

    // Конструктор маппинга для Live-событий (snapshotId здесь не нужен)
    public static GiftHistoryDocument fromDto(GetGemsItemDto dto) {
        GiftHistoryDocument doc = new GiftHistoryDocument();

        doc.setCollectionAddress(dto.getCollectionAddress());
        doc.setAddress(dto.getAddress());
        doc.setHash(dto.getHash());
        doc.setLt(dto.getLt());
        doc.setName(dto.getName());
        doc.setTimestamp(dto.getTimestamp());
        doc.setIsOffchain(dto.isOffchain());

        if (dto.getTypeData() != null) {
            doc.setEventType(dto.getTypeData().getType());
            doc.setPrice(dto.getTypeData().getPrice());
            doc.setPriceNano(dto.getTypeData().getPriceNano());
            doc.setCurrency(dto.getTypeData().getCurrency());
            doc.setOldOwner(dto.getTypeData().getOldOwner());
            doc.setNewOwner(dto.getTypeData().getNewOwner());
        }
        return doc;
    }
}