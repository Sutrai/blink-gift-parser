package com.ceawse.portalsparser.worker;

import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.domain.PortalsIngestionState;
import com.ceawse.portalsparser.dto.PortalsActionsResponseDto;
import com.ceawse.portalsparser.repository.PortalsGiftHistoryRepository;
import com.ceawse.portalsparser.repository.PortalsIngestionStateRepository;
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

    private static final String PROCESS_ID = "PORTALS_LIVE";

    @Scheduled(fixedDelay = 3000)
    public void pollEvents() {
        try {
            PortalsIngestionState state = stateRepository.findById(PROCESS_ID)
                    .orElse(new PortalsIngestionState(PROCESS_ID, System.currentTimeMillis() - 60000));

            // ИСПРАВЛЕНИЕ ЗДЕСЬ:
            // Было: "latest"
            // Стало: "listed_at desc"
            // Также action_types передаем через запятую, Feign сам закодирует их в %2C
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

                PortalsGiftHistoryDocument doc = mapToEntity(action, actionTime);

                // Используем offerId как базу для хеша, так как он есть в DTO
                if (doc.getHash() != null && !historyRepository.existsByHash(doc.getHash())) {
                    historyRepository.save(doc);
                    savedCount++;
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

    // mapToEntity и другие методы остаются без изменений...
    private PortalsGiftHistoryDocument mapToEntity(PortalsActionsResponseDto.ActionDto action, long timestamp) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace("portals");
        doc.setTimestamp(timestamp);
        doc.setIsOffchain(true);

        if (action.getNft() != null) {
            doc.setAddress(action.getNft().getId());
            doc.setCollectionAddress(action.getNft().getCollectionId());
            doc.setName(action.getNft().getName());
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