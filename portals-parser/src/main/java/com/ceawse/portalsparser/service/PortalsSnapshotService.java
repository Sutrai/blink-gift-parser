package com.ceawse.portalsparser.service;

import com.ceawse.portalsparser.client.PortalsApiClient;
import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import com.ceawse.portalsparser.domain.UniqueGiftDocument;
import com.ceawse.portalsparser.dto.PortalsSearchResponseDto;
import com.ceawse.portalsparser.dto.PortalsNftDto;
import com.ceawse.portalsparser.repository.PortalsGiftHistoryRepository;
import com.ceawse.portalsparser.repository.PortalsUniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalsSnapshotService {

    private final PortalsApiClient apiClient;
    private final PortalsGiftHistoryRepository historyRepository;
    private final PortalsUniqueGiftRepository uniqueGiftRepository;

    @Async
    public void runSnapshot() {
        String snapshotId = UUID.randomUUID().toString();
        log.info("Starting Portals SNAPSHOT id={}", snapshotId);
        long startTime = System.currentTimeMillis();
        int offset = 0;
        int limit = 50;
        boolean hasMore = true;

        try {
            while (hasMore) {
                // sort_by=price+asc ищем по возрастанию цены (ликвидность)
                PortalsSearchResponseDto response = apiClient.searchNfts(
                        offset, limit, "price asc", "listed", true
                );

                if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                    hasMore = false;
                    continue;
                }

                List<PortalsNftDto> items = response.getResults();

                // 1. Сохраняем историю (Listings)
                List<PortalsGiftHistoryDocument> events = items.stream()
                        .map(nft -> mapToSnapshotEvent(nft, snapshotId))
                        .toList();
                historyRepository.saveAll(events);

                // 2. Сохраняем атрибуты и правильные имена в unique_gifts
                items.forEach(this::saveUniqueGiftWithAttributes);

                log.info("Portals Snapshot: saved {} items, offset {}", events.size(), offset);
                offset += limit;

                // Небольшая задержка
                Thread.sleep(200);
            }
            finishSnapshot(snapshotId, startTime);
        } catch (Exception e) {
            log.error("Portals Snapshot failed", e);
        }
    }

    private void saveUniqueGiftWithAttributes(PortalsNftDto nft) {
        if (nft.getAttributes() == null || nft.getAttributes().isEmpty()) return;

        // Формируем имя: Name #12345
        String formattedName = formatName(nft.getName(), nft.getExternalCollectionNumber());

        UniqueGiftDocument.GiftAttributes.GiftAttributesBuilder attrsBuilder = UniqueGiftDocument.GiftAttributes.builder();
        attrsBuilder.updatedAt(Instant.now());

        // Парсим только нужные атрибуты, игнорируя rarity
        for (PortalsNftDto.AttributeDto attr : nft.getAttributes()) {
            if ("model".equalsIgnoreCase(attr.getType())) attrsBuilder.model(attr.getValue());
            if ("backdrop".equalsIgnoreCase(attr.getType())) attrsBuilder.backdrop(attr.getValue());
            if ("symbol".equalsIgnoreCase(attr.getType())) attrsBuilder.symbol(attr.getValue());
        }

        UniqueGiftDocument doc = UniqueGiftDocument.builder()
                .id(nft.getId()) // Portals использует UUID
                .name(formattedName)
                .collectionAddress(nft.getCollectionId())
                .attributes(attrsBuilder.build())
                .lastSeenAt(Instant.now())
                .build();

        // save работает как upsert (обновит, если ID совпадает)
        uniqueGiftRepository.save(doc);
    }

    private PortalsGiftHistoryDocument mapToSnapshotEvent(PortalsNftDto nft, String snapshotId) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace("portals");
        doc.setEventType("SNAPSHOT_LIST");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setAddress(nft.getId());
        doc.setCollectionAddress(nft.getCollectionId());

        // Формируем правильное имя и здесь тоже
        doc.setName(formatName(nft.getName(), nft.getExternalCollectionNumber()));

        doc.setIsOffchain(true);
        doc.setHash(snapshotId + "_" + nft.getId());

        if (nft.getPrice() != null) {
            doc.setPrice(nft.getPrice());
            doc.setPriceNano(toNano(nft.getPrice()));
            doc.setCurrency("TON");
        }
        return doc;
    }

    private String formatName(String rawName, Long number) {
        if (number != null) {
            return rawName + " #" + number;
        }
        return rawName;
    }

    private void finishSnapshot(String snapshotId, long startTime) {
        PortalsGiftHistoryDocument doc = new PortalsGiftHistoryDocument();
        doc.setMarketplace("portals");
        doc.setEventType("SNAPSHOT_FINISH");
        doc.setSnapshotId(snapshotId);
        doc.setTimestamp(System.currentTimeMillis());
        doc.setEventPayload(String.valueOf(startTime));
        doc.setPriceNano("0");
        doc.setHash("PORTALS_FINISH_" + snapshotId);
        doc.setAddress("SYSTEM_PORTALS");
        doc.setCollectionAddress("SYSTEM_PORTALS");
        historyRepository.save(doc);
        log.info("Portals Snapshot {} FINISHED.", snapshotId);
    }

    private String toNano(String price) {
        try {
            return new BigDecimal(price).multiply(BigDecimal.valueOf(1_000_000_000)).toBigInteger().toString();
        } catch (Exception e) {
            return "0";
        }
    }
}