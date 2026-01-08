package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.client.GetGemsApiClient;
import com.ceawse.blinkgift.client.IndexerApiClient;
import com.ceawse.blinkgift.domain.CollectionStatisticsDocument;
import com.ceawse.blinkgift.dto.GetGemsCollectionDto;
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

    // Запускаем раз в 10 минут (600000 мс), чтобы обновлять флоры
    @Scheduled(fixedDelay = 30000)
    public void parseStatistics() {
        log.info("Starting collection statistics parser...");
        try {
            // 1. Получаем список всех коллекций от индексера
            List<IndexerApiClient.CollectionDto> collections = indexerClient.getCollections();
            log.info("Found {} collections to update stats.", collections.size());

            // 2. Проходимся по каждой
            for (IndexerApiClient.CollectionDto collection : collections) {
                processCollectionStats(collection.address);

                // Небольшая пауза, чтобы не упереться в лимиты GetGems
                Thread.sleep(250);
            }
        } catch (Exception e) {
            log.error("Global error in StatisticsParserWorker", e);
        }
        log.info("Collection statistics parser finished.");
    }

    private void processCollectionStats(String address) {
        try {
            // Запрос в GetGems
            GetGemsCollectionDto dto = getGemsClient.getCollection(address);

            if (dto == null || !dto.isSuccess() || dto.getResponse() == null) {
                return;
            }

            var data = dto.getResponse();
            var stats = data.getStatistics();

            // Парсинг цены (флора)
            BigDecimal floor = null;
            Long floorNano = null;

            if (stats != null) {
                if (stats.getFloorPriceNano() != null) {
                    // Если есть nano цена
                    floorNano = Long.parseLong(stats.getFloorPriceNano());
                    floor = BigDecimal.valueOf(floorNano).divide(BigDecimal.valueOf(1_000_000_000));
                } else if (stats.getFloorPrice() != null) {
                    // Если только обычная строка
                    try {
                        floor = new BigDecimal(stats.getFloorPrice());
                        floorNano = floor.multiply(BigDecimal.valueOf(1_000_000_000)).longValue();
                    } catch (Exception ignored) {}
                }
            }

            // Создаем или обновляем документ статистики
            CollectionStatisticsDocument doc = CollectionStatisticsDocument.builder()
                    .collectionAddress(address)
                    .name(data.getName())
                    .itemsCount(stats != null ? stats.getItemsCount() : 0)
                    .ownersCount(stats != null ? stats.getOwnersCount() : 0)
                    .floorPrice(floor)
                    .floorPriceNano(floorNano)
                    .updatedAt(Instant.now())
                    .build();

            statisticsRepository.save(doc);

        } catch (FeignException.NotFound e) {
            // 404 - коллекция есть в блокчейне, но нет на GetGems
            log.warn("Collection not found on GetGems: {}", address);
        } catch (Exception e) {
            log.error("Failed to update stats for {}: {}", address, e.getMessage());
        }
    }
}