package com.ceawse.portalsparser.worker;

import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.domain.PortalsIngestionState;
import com.ceawse.portalsparser.domain.UniqueGiftDocument;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.dto.PortalsNftDto;
import com.ceawse.portalsparser.repository.PortalsGiftHistoryRepository;
import com.ceawse.portalsparser.repository.PortalsIngestionStateRepository;
import com.ceawse.portalsparser.repository.PortalsUniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortalsRealtimeWorker {

    private final PortalsApiClient apiClient;
    private final PortalsGiftHistoryRepository historyRepository;
    private final PortalsIngestionStateRepository stateRepository;
    private final PortalsUniqueGiftRepository uniqueGiftRepository;

    private static final String PROCESS_ID = "PORTALS_LIVE";

    @Scheduled(fixedDelay = 3000)
    public void pollEvents() {
        try {
            PortalsIngestionState state = stateRepository.findById(PROCESS_ID)
                    .orElse(new PortalsIngestionState(PROCESS_ID, System.currentTimeMillis() - 60000));

            PortalsActionsResponseDto response = apiClient.getMarketActivity(
                    0, 50, "listed_at desc", "buy,listing,price_update"
            );

            if (response == null || response.getActions() == null || response.getActions().isEmpty()) {
                return;
            }

            List<PortalsActionsResponseDto.ActionDto> actions = response.getActions();
            long lastProcessedTime = state.getLastProcessedTimestamp();
            long newMaxTime = lastProcessedTime;
            int savedCount = 0;

            for (PortalsActionsResponseDto.ActionDto action : actions) {
                long actionTime = parseTime(action.getCreatedAt());

                if (actionTime <= lastProcessedTime) {
                    continue;
                }

                // 1. Сохраняем событие истории
                PortalsGiftHistoryDocument doc = mapToEntity(action, actionTime);
                if (doc.getHash() != null && !historyRepository.existsByHash(doc.getHash())) {
                    historyRepository.save(doc);
                    savedCount++;
                }

                // 2. Если есть данные NFT, сохраняем атрибуты в unique_gifts
                if (action.getNft() != null) {
                    saveUniqueGiftWithAttributes(action.getNft());
                }

                if (actionTime > newMaxTime) {
                    newMaxTime = actionTime;
                }
            }

            if (savedCount > 0) {
                log.info("Portals Realtime: saved {} new events.", savedCount);
                state.setLastProcessedTimestamp(newMaxTime);
                stateRepository.save(state);
            }

        } catch (Exception e) {
            log.error("Error polling Portals API: {}", e.getMessage());
        }
    }

    private void saveUniqueGiftWithAttributes(PortalsNftDto nft) {
        if (nft.getAttributes() == null || nft.getAttributes().isEmpty()) return;

        String formattedName = formatName(nft.getName(), nft.getExternalCollectionNumber());

        UniqueGiftDocument.GiftAttributes.GiftAttributesBuilder attrsBuilder = UniqueGiftDocument.GiftAttributes.builder();
        attrsBuilder.updatedAt(Instant.now());

        for (PortalsNftDto.AttributeDto attr : nft.getAttributes()) {
            if ("model".equalsIgnoreCase(attr.getType())) attrsBuilder.model(attr.getValue());
            if ("backdrop".equalsIgnoreCase(attr.getType())) attrsBuilder.backdrop(attr.getValue());
            if ("symbol".equalsIgnoreCase(attr.getType())) attrsBuilder.symbol(attr.getValue());
        }

        UniqueGiftDocument doc = UniqueGiftDocument.builder()
                .id(nft.getId())
                .name(formattedName)
                .collectionAddress(nft.getCollectionId())
                .attributes(attrsBuilder.build())
                .lastSeenAt(Instant.now())
                .build();

        uniqueGiftRepository.save(doc);
    }

    private PortalsGiftHistoryDocument mapToEntity(PortalsActionsResponseDto.ActionDto action, long timestamp) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace("portals"); // Явно указываем маркетплейс
        doc.setTimestamp(timestamp);
        doc.setIsOffchain(true);

        if (action.getNft() != null) {
            doc.setAddress(action.getNft().getId());
            doc.setCollectionAddress(action.getNft().getCollectionId());
            // Формируем правильное имя
            doc.setName(formatName(action.getNft().getName(), action.getNft().getExternalCollectionNumber()));
        } else {
            doc.setAddress("UNKNOWN");
            doc.setName("UNKNOWN");
        }

        String sysType = "UNKNOWN";
        if (action.getType() != null) {
            switch (action.getType()) {
                case "listing":
                    sysType = "PUTUPFORSALE";
                    break;
                case "buy":
                    sysType = "SOLD";
                    break;
                case "price_update":
                    sysType = "PUTUPFORSALE";
                    break;
                default:
                    sysType = action.getType().toUpperCase();
            }
        }
        doc.setEventType(sysType);

        if (action.getAmount() != null) {
            doc.setPrice(action.getAmount());
            doc.setPriceNano(toNano(action.getAmount()));
            doc.setCurrency("TON");
        }

        String uniqueHash = (action.getOfferId() != null ? action.getOfferId() : "no_id")
                + "_" + action.getType()
                + "_" + timestamp;

        doc.setHash(uniqueHash);

        return doc;
    }

    private String formatName(String rawName, Long number) {
        if (number != null) {
            return rawName + " #" + number;
        }
        return rawName;
    }

    private long parseTime(String isoTime) {
        try {
            return Instant.parse(isoTime).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String toNano(String price) {
        try {
            return new BigDecimal(price).multiply(BigDecimal.valueOf(1_000_000_000)).toBigInteger().toString();
        } catch (Exception e) {
            return "0";
        }
    }
}