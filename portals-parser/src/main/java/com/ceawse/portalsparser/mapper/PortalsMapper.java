package com.ceawse.portalsparser.mapper;

import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.domain.UniqueGiftDocument;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsNftDto;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
public class PortalsMapper {

    private static final String MARKETPLACE_NAME = "portals";
    private static final String CURRENCY_TON = "TON";
    private static final BigDecimal NANO_MULTIPLIER = BigDecimal.valueOf(1_000_000_000);

    public PortalsGiftHistoryDocument mapSnapshotToHistory(PortalsNftDto nft, String snapshotId) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace(MARKETPLACE_NAME);
        doc.setEventType("SNAPSHOT_LIST");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setAddress(nft.getId());
        doc.setCollectionAddress(nft.getCollectionId());
        doc.setName(formatName(nft.getName(), nft.getExternalCollectionNumber()));
        doc.setIsOffchain(true);
        doc.setHash(snapshotId + "_" + nft.getId());

        if (StringUtils.hasText(nft.getPrice())) {
            doc.setPrice(nft.getPrice());
            doc.setPriceNano(toNano(nft.getPrice()));
            doc.setCurrency(CURRENCY_TON);
        }
        return doc;
    }

    public UniqueGiftDocument mapToUniqueGift(PortalsNftDto nft) {
        String formattedName = formatName(nft.getName(), nft.getExternalCollectionNumber());

        UniqueGiftDocument.GiftAttributes attributes = extractAttributes(nft.getAttributes());

        return UniqueGiftDocument.builder()
                .id(nft.getId())
                .name(formattedName)
                .collectionAddress(nft.getCollectionId())
                .attributes(attributes)
                .lastSeenAt(Instant.now())
                .build();
    }

    public PortalsGiftHistoryDocument mapActionToHistory(PortalsActionsResponseDto.ActionDto action, long timestamp) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace(MARKETPLACE_NAME);
        doc.setTimestamp(timestamp);
        doc.setIsOffchain(true);

        if (action.getNft() != null) {
            doc.setAddress(action.getNft().getId());
            doc.setCollectionAddress(action.getNft().getCollectionId());
            doc.setName(formatName(action.getNft().getName(), action.getNft().getExternalCollectionNumber()));
        } else {
            doc.setAddress("UNKNOWN");
            doc.setName("UNKNOWN");
        }

        doc.setEventType(mapEventType(action.getType()));

        if (StringUtils.hasText(action.getAmount())) {
            doc.setPrice(action.getAmount());
            doc.setPriceNano(toNano(action.getAmount()));
            doc.setCurrency(CURRENCY_TON);
        }

        String uniqueHash = (action.getOfferId() != null ? action.getOfferId() : "no_id")
                + "_" + action.getType()
                + "_" + timestamp;

        doc.setHash(uniqueHash);

        return doc;
    }

    public PortalsGiftHistoryDocument createSnapshotFinishEvent(String snapshotId, long startTime) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace(MARKETPLACE_NAME);
        doc.setEventType("SNAPSHOT_FINISH");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setEventPayload(String.valueOf(startTime));
        doc.setPriceNano("0");
        doc.setHash("PORTALS_FINISH_" + snapshotId);
        doc.setAddress("SYSTEM_PORTALS");
        doc.setCollectionAddress("SYSTEM_PORTALS");
        return doc;
    }

    private String formatName(String rawName, Long number) {
        if (number != null) {
            return rawName + " #" + number;
        }
        return rawName;
    }

    private UniqueGiftDocument.GiftAttributes extractAttributes(List<PortalsNftDto.AttributeDto> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        UniqueGiftDocument.GiftAttributes.GiftAttributesBuilder builder = UniqueGiftDocument.GiftAttributes.builder();
        builder.updatedAt(Instant.now());

        for (PortalsNftDto.AttributeDto attr : attributes) {
            if ("model".equalsIgnoreCase(attr.getType())) builder.model(attr.getValue());
            if ("backdrop".equalsIgnoreCase(attr.getType())) builder.backdrop(attr.getValue());
            if ("symbol".equalsIgnoreCase(attr.getType())) builder.symbol(attr.getValue());
        }
        return builder.build();
    }

    private String mapEventType(String rawType) {
        if (rawType == null) return "UNKNOWN";
        return switch (rawType.toLowerCase()) {
            case "listing", "price_update" -> "PUTUPFORSALE";
            case "buy" -> "SOLD";
            default -> rawType.toUpperCase();
        };
    }

    private String toNano(String price) {
        try {
            return new BigDecimal(price).multiply(NANO_MULTIPLIER).toBigInteger().toString();
        } catch (Exception e) {
            return "0";
        }
    }
}