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

    // --- ВАЖНОЕ НОВОЕ ПОЛЕ ---
    @Indexed // Индекс нужен, чтобы быстро искать историю конкретной NFT
    private String address;
    // -------------------------

    @Indexed(unique = true)
    private String hash;

    private String lt;
    private String name;
    private Long timestamp;
    private String eventType; // mint, sold, transfer

    // --- НОВОЕ ПОЛЕ ---
    private Boolean isOffchain;
    // ------------------

    // Детали сделки
    private String price;

    // --- НОВОЕ ПОЛЕ ---
    private String priceNano; // Сохраняем точную цену
    // ------------------

    private String currency;
    private String oldOwner;
    private String newOwner;

    // Конструктор маппинга
    public static GiftHistoryDocument fromDto(GetGemsItemDto dto) {
        GiftHistoryDocument doc = new GiftHistoryDocument();

        // 1. Заполняем основные поля
        doc.setCollectionAddress(dto.getCollectionAddress());

        // ВОТ ЗДЕСЬ РАНЬШЕ ТЕРЯЛСЯ АДРЕС
        doc.setAddress(dto.getAddress());

        doc.setHash(dto.getHash());
        doc.setLt(dto.getLt());
        doc.setName(dto.getName());
        doc.setTimestamp(dto.getTimestamp());
        doc.setIsOffchain(dto.isOffchain()); // Сохраняем флаг

        // 2. Заполняем детали из typeData
        if (dto.getTypeData() != null) {
            doc.setEventType(dto.getTypeData().getType());
            doc.setPrice(dto.getTypeData().getPrice());
            doc.setPriceNano(dto.getTypeData().getPriceNano()); // Сохраняем нанотоны
            doc.setCurrency(dto.getTypeData().getCurrency());
            doc.setOldOwner(dto.getTypeData().getOldOwner());
            doc.setNewOwner(dto.getTypeData().getNewOwner());
        }
        return doc;
    }
}
