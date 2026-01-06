package com.ceawse.blinkgift.mapper;

import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import com.ceawse.blinkgift.dto.GetGemsItemDto;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public GiftHistoryDocument toEntity(GetGemsItemDto dto) {
        GiftHistoryDocument doc = new GiftHistoryDocument();

        doc.setCollectionAddress(dto.getCollectionAddress());
        doc.setAddress(dto.getAddress());
        doc.setName(dto.getName());
        doc.setTimestamp(dto.getTimestamp());
        doc.setHash(dto.getHash());
        doc.setLt(dto.getLt());
        doc.setIsOffchain(dto.isOffchain());

        // Маппинг специфичных данных
        if (dto.getTypeData() != null) {
            var typeData = dto.getTypeData();
            doc.setEventType(normalizeEventType(typeData.getType()));
            doc.setPrice(typeData.getPrice());
            doc.setPriceNano(typeData.getPriceNano());
            doc.setCurrency(typeData.getCurrency());
            doc.setOldOwner(typeData.getOldOwner());
            doc.setNewOwner(typeData.getNewOwner());
        }

        // Важно: помечаем источник данных, чтобы потом различать GetGems и Portals
        // doc.setSource("GETGEMS"); // Если добавишь такое поле в сущность

        return doc;
    }

    private String normalizeEventType(String rawType) {
        if (rawType == null) return "UNKNOWN";
        return rawType.toLowerCase(); // Можно мапить в Enum
    }
}