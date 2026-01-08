package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.CollectionStatisticsDocument;
import com.ceawse.blinkgift.dto.GetGemsCollectionDto;
import com.ceawse.blinkgift.dto.GetGemsCollectionStatsDto;
import com.ceawse.blinkgift.repository.CollectionStatisticsRepository;
import feign.FeignException;
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
public class StatisticsParserWorker {

    private final IndexerApiClient indexerClient;
    private final GetGemsApiClient getGemsClient;
    private final CollectionStatisticsRepository statisticsRepository;

    @Scheduled(fixedDelay = 30000)
    public void parseStatistics() {
        log.info("Starting collection statistics parser...");
        try {
            List<IndexerApiClient.CollectionDto> collections = indexerClient.getCollections();
            log.info("Found {} collections to update stats.", collections.size());

            for (IndexerApiClient.CollectionDto collection : collections) {
                processCollectionStats(collection.address);

                Thread.sleep(250);
            }
        } catch (Exception e) {
            log.error("Global error in StatisticsParserWorker", e);
        }
        log.info("Collection statistics parser finished.");
    }

    private void processCollectionStats(String address) {
        try {
            GetGemsCollectionStatsDto dto = getGemsClient.getCollectionStats(address);

            if (dto == null || !dto.isSuccess() || dto.getResponse() == null) {
                return;
            }

            var stats = dto.getResponse();

            BigDecimal floor = null;
            Long floorNano = null;

            if (stats.getFloorPriceNano() != null) {
                try {
                    floorNano = Long.parseLong(stats.getFloorPriceNano());
                    floor = BigDecimal.valueOf(floorNano).divide(BigDecimal.valueOf(1_000_000_000));
                } catch (NumberFormatException e) {
                    log.warn("Invalid floorPriceNano format: {}", stats.getFloorPriceNano());
                }
            } else if (stats.getFloorPrice() != null) {
                // Если только обычная строка
                try {
                    floor = new BigDecimal(stats.getFloorPrice());
                    floorNano = floor.multiply(BigDecimal.valueOf(1_000_000_000)).longValue();
                } catch (Exception ignored) {}
            }

            CollectionStatisticsDocument doc = CollectionStatisticsDocument.builder()
                    .collectionAddress(address)
                    // .name(collectionName) // Имя получить неоткуда в этом запросе!
                    .itemsCount(stats.getItemsCount() != null ? stats.getItemsCount() : 0)
                    .ownersCount(stats.getOwnersCount() != null ? stats.getOwnersCount() : 0) // Теперь берется из поля mapped holders
                    .floorPrice(floor)
                    .floorPriceNano(floorNano)
                    .updatedAt(Instant.now())
                    .build();

            statisticsRepository.save(doc);

        } catch (FeignException.NotFound e) {
            log.warn("Collection not found on GetGems: {}", address);
        } catch (Exception e) {
            log.error("Failed to update stats for {}: {}", address, e.getMessage(), e);
        }
    }
}