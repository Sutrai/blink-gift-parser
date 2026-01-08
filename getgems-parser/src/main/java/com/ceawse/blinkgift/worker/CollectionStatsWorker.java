package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.CollectionStatsDocument;
import com.ceawse.blinkgift.dto.GetGemsCollectionStatsDto;
import com.ceawse.blinkgift.repository.CollectionStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionStatsWorker {

    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final CollectionStatsRepository statsRepository;

    @Scheduled(fixedDelay = 60000) // Раз в минуту
    public void parseStats() {
        log.info("Starting collection stats parsing...");
        try {
            var collections = indexerClient.getCollections();
            for (var col : collections) {
                updateCollectionStats(col.address);
                Thread.sleep(200); // Rate limit protection
            }
        } catch (Exception e) {
            log.error("Global stats parsing error", e);
        }
    }

    private void updateCollectionStats(String address) {
        try {
            GetGemsCollectionStatsDto dto = getGemsClient.getCollectionStats(address);
            if (dto != null && dto.isSuccess() && dto.getResponse() != null) {
                var stats = dto.getResponse().getStats();

                Long floorNano = stats.getFloorPriceNano() != null ? Long.parseLong(stats.getFloorPriceNano()) : 0L;

                CollectionStatsDocument doc = CollectionStatsDocument.builder()
                        .collectionAddress(address)
                        .name(dto.getResponse().getName())
                        .floorPriceNano(floorNano)
                        .floorPrice(stats.getFloorPrice())
                        .volume(stats.getVolume())
                        .updatedAt(Instant.now())
                        .build();

                statsRepository.save(doc);
                log.debug("Updated stats for {}", address);
            }
        } catch (Exception e) {
            log.error("Failed to update stats for {}", address, e);
        }
    }
}