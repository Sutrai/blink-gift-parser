package com.ceawse.onchainindexer.worker;

import com.ceawse.onchainindexer.client.DiscoveryInternalClient;
import com.ceawse.onchainindexer.client.FragmentMetadataClient;
import com.ceawse.onchainindexer.client.RegistryApiClient;
import com.ceawse.onchainindexer.dto.FragmentMetadataDto;
import com.ceawse.onchainindexer.dto.InternalEnrichmentDto;
import com.ceawse.onchainindexer.model.CollectionRegistryDocument;
import com.ceawse.onchainindexer.model.UniqueGiftDocument;
import com.ceawse.onchainindexer.repository.CollectionRegistryRepository;
import com.ceawse.onchainindexer.repository.UniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexerWorker {

    private final RegistryApiClient apiClient;
    private final FragmentMetadataClient fragmentClient;
    private final DiscoveryInternalClient discoveryClient;

    private final CollectionRegistryRepository colRepo;
    private final UniqueGiftRepository uniqueGiftRepo;

    private static final List<String> MINT_TYPE = List.of("mint");

    @Scheduled(fixedDelay = 1000)
    public void indexNextBatch() {
        colRepo.findFirstByEnabledTrueOrderByLastScanTimestampAsc()
                .ifPresent(this::processCollection);
    }

    private void processCollection(CollectionRegistryDocument col) {
        try {
            var response = apiClient.getCollectionHistory(col.getAddress(), 50, col.getLastHistoryCursor(), MINT_TYPE, false);

            if (response == null || !response.isSuccess() || response.getResponse().getItems().isEmpty()) {
                updateTimestampOnly(col);
                return;
            }

            for (var dto : response.getResponse().getItems()) {
                if (dto.getAddress() == null) continue;
                processSingleGift(dto, col);
            }

            col.setLastHistoryCursor(response.getResponse().getCursor());
            col.setLastScanTimestamp(System.currentTimeMillis());
            colRepo.save(col);

        } catch (Exception e) {
            log.error("Error indexing {}: {}", col.getName(), e.getMessage());
            updateTimestampOnly(col);
        }
    }

    private void processSingleGift(com.ceawse.onchainindexer.dto.CollectionHistoryDto.HistoryItemDto dto, CollectionRegistryDocument col) {
        try {
            // 1. Формируем SLUG для Fragment (Plush Pepe #2658 -> plushpepe-2658)
            String slug = formatSlug(dto.getName());

            // 2. Получаем атрибуты из Fragment
            FragmentMetadataDto metadata = fragmentClient.getMetadata(slug);
            String model = extractAttr(metadata, "Model");
            String backdrop = extractAttr(metadata, "Backdrop");
            String symbol = extractAttr(metadata, "Symbol");

            // 3. Вызываем обогащение из модуля gift-discovery
            var enrichReq = InternalEnrichmentDto.Request.builder()
                    .giftName(dto.getName())
                    .collectionAddress(col.getAddress())
                    .model(model)
                    .backdrop(backdrop)
                    .symbol(symbol)
                    .build();

            var enrichRes = discoveryClient.calculate(enrichReq);

            // 4. Сохраняем в unique_gifts
            UniqueGiftDocument gift = UniqueGiftDocument.builder()
                    .id(dto.getAddress())
                    .name(dto.getName())
                    .collectionAddress(enrichRes.getResolvedCollectionAddress() != null ? enrichRes.getResolvedCollectionAddress() : col.getAddress())
                    .isOffchain(false)
                    .discoverySource("ONCHAIN_INDEXER")
                    .firstSeenAt(Instant.ofEpochMilli(dto.getTimestamp()))
                    .lastSeenAt(Instant.now())
                    .attributes(UniqueGiftDocument.GiftAttributes.builder()
                            .model(model)
                            .modelPrice(enrichRes.getModelPrice())
                            .modelRarityCount(enrichRes.getModelCount())
                            .backdrop(backdrop)
                            .backdropPrice(enrichRes.getBackdropPrice())
                            .backdropRarityCount(enrichRes.getBackdropCount())
                            .symbol(symbol)
                            .symbolPrice(enrichRes.getSymbolPrice())
                            .symbolRarityCount(enrichRes.getSymbolCount())
                            .updatedAt(Instant.now())
                            .build())
                    .marketData(UniqueGiftDocument.MarketData.builder()
                            .collectionFloorPrice(enrichRes.getCollectionFloorPrice())
                            .estimatedPrice(enrichRes.getEstimatedPrice())
                            .priceUpdatedAt(Instant.now())
                            .build())
                    .build();

            uniqueGiftRepo.save(gift);
            log.info("Successfully indexed and enriched gift: {}", dto.getName());

        } catch (Exception e) {
            log.warn("Failed to fully process gift {}: {}", dto.getName(), e.getMessage());
        }
    }

    private String formatSlug(String name) {
        if (name == null) return "";
        int hashIndex = name.lastIndexOf('#');
        if (hashIndex != -1) {
            String base = name.substring(0, hashIndex).toLowerCase().replaceAll("[\\s'’\\-]", "");
            String num = name.substring(hashIndex + 1).trim();
            return base + "-" + num;
        }
        return name.toLowerCase().replaceAll("[\\s'’\\-]", "");
    }

    private String extractAttr(FragmentMetadataDto meta, String type) {
        if (meta == null || meta.getAttributes() == null) return null;
        return meta.getAttributes().stream()
                .filter(a -> type.equalsIgnoreCase(a.getTrait_type()))
                .map(FragmentMetadataDto.AttributeDto::getValue)
                .findFirst()
                .orElse(null);
    }

    private void updateTimestampOnly(CollectionRegistryDocument col) {
        col.setLastScanTimestamp(System.currentTimeMillis());
        colRepo.save(col);
    }
}