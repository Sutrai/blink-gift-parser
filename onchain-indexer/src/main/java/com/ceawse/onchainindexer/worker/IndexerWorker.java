package com.ceawse.onchainindexer.worker;

import com.ceawse.onchainindexer.client.DiscoveryInternalClient;
import com.ceawse.onchainindexer.client.FragmentMetadataClient;
import com.ceawse.onchainindexer.client.RegistryApiClient;
import com.ceawse.onchainindexer.dto.FragmentMetadataDto;
import com.ceawse.onchainindexer.dto.InternalEnrichmentDto;
import com.ceawse.onchainindexer.model.CollectionRegistryDocument;
import com.ceawse.onchainindexer.repository.CollectionRegistryRepository;
import com.ceawse.onchainindexer.repository.UniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    // Тот самый метод, который ты вызываешь из контроллера
    @Async
    public void runFullIndexing() {
        log.info("Manual full indexing triggered...");
        colRepo.findAll().stream()
                .filter(CollectionRegistryDocument::isEnabled)
                .forEach(this::processCollection);
        log.info("Full indexing completed.");
    }

    @Scheduled(fixedDelay = 10000) // Фоновый скан каждые 10 сек
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
            if (uniqueGiftRepo.existsById(dto.getAddress())) return;

            log.info("New gift: {}. Fetching attributes...", dto.getName());

            String slug = formatSlug(dto.getName());
            FragmentMetadataDto metadata = fragmentClient.getMetadata(slug);

            String model = extractAttr(metadata, "Model");
            String backdrop = extractAttr(metadata, "Backdrop");
            String symbol = extractAttr(metadata, "Symbol");

            // --- ПАРСИМ НОМЕР ИЗ ИМЕНИ ---
            Integer serial = null;
            if (dto.getName() != null && dto.getName().contains("#")) {
                try {
                    serial = Integer.parseInt(dto.getName().split("#")[1].trim());
                } catch (Exception e) {
                    log.warn("Could not parse serial number from {}", dto.getName());
                }
            }

            var enrichReq = InternalEnrichmentDto.Request.builder()
                    .id(dto.getAddress())
                    .timestamp(dto.getTimestamp())
                    .giftName(dto.getName())
                    .collectionAddress(col.getAddress())
                    .serialNumber(serial) // ПЕРЕДАЕМ НОМЕР
                    .totalLimit(null)      // Можно будет добавить логику получения лимита коллекции
                    .model(model)
                    .backdrop(backdrop)
                    .symbol(symbol)
                    .build();

            discoveryClient.calculate(enrichReq);

        } catch (Exception e) {
            log.warn("Failed to process gift {}: {}", dto.getName(), e.getMessage());
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