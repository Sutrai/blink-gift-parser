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
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortalsRealtimeWorker {

    private final PortalsApiClient apiClient;
    private final PortalsGiftHistoryRepository historyRepository;
    private final PortalsIngestionStateRepository stateRepository;

    private static final String PROCESS_ID = "PORTALS_LIVE";

    // Опрашиваем каждые 3 секунды
    @Scheduled(fixedDelay = 3000)
    public void pollEvents() {
        try {
            PortalsIngestionState state = stateRepository.findById(PROCESS_ID)
                    .orElse(new PortalsIngestionState(PROCESS_ID, System.currentTimeMillis() - 60000)); // Дефолт: минуту назад

            // Запрашиваем "buy,listing,price_update"
            // Сортировка "latest" означает самые новые первыми в списке
            PortalsActionsResponseDto response = apiClient.getMarketActivity(
                    0, 50, "latest", "buy,listing,price_update"
            );

            if (response == null || response.getActions() == null || response.getActions().isEmpty()) {
                return;
            }

            List<PortalsActionsResponseDto.ActionDto> actions = response.getActions();
            // Разворачиваем, чтобы обрабатывать от старых к новым (если нужно),
            // но для фильтрации по времени удобнее просто идти по списку

            long lastProcessedTime = state.getLastProcessedTimestamp();
            long newMaxTime = lastProcessedTime;

            int savedCount = 0;

            for (PortalsActionsResponseDto.ActionDto action : actions) {
                long actionTime = parseTime(action.getCreatedAt());

                // Пропускаем уже обработанные
                if (actionTime <= lastProcessedTime) {
                    continue;
                }

                // Маппим
                PortalsGiftHistoryDocument doc = mapToEntity(action, actionTime);
                doc.setMarketplace("portals");

                // Дедупликация по хешу (offer_id в Portals может быть неуникальным для разных типов действий?
                // Обычно activity id уникален. Используем комбинацию если надо, но пока offer_id + type)
                // Примечание: action.offerId может быть null в некоторых случаях, нужно проверять
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

    private PortalsGiftHistoryDocument mapToEntity(PortalsActionsResponseDto.ActionDto action, long timestamp) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
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

        // Mapping types to System standard
        // Portals: buy, listing, price_update
        // System: SOLD, PUTUPFORSALE, CANCELSALE
        String sysType = "UNKNOWN";
        switch (action.getType()) {
            case "listing":
                sysType = "PUTUPFORSALE";
                break;
            case "buy":
                sysType = "SOLD";
                break;
            case "price_update":
                sysType = "PUTUPFORSALE"; // Обновление цены = новый листинг в логике истории
                break;
            default:
                sysType = action.getType().toUpperCase();
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