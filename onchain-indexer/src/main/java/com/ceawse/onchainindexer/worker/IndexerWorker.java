package com.ceawse.onchainindexer.worker;

import com.ceawse.onchainindexer.client.RegistryApiClient;
import com.ceawse.onchainindexer.dto.CollectionHistoryDto;
import com.ceawse.onchainindexer.model.CollectionRegistryDocument;
import com.ceawse.onchainindexer.model.ItemRegistryDocument;
import com.ceawse.onchainindexer.repository.CollectionRegistryRepository;
import com.ceawse.onchainindexer.repository.ItemRegistryRepository;
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
    private final CollectionRegistryRepository colRepo;
    private final ItemRegistryRepository itemRepo;

    private static final List<String> MINT_TYPE = List.of("mint");

    @Scheduled(fixedDelay = 1000)
    public void indexNextBatch() {
        var collectionOpt = colRepo.findFirstByEnabledTrueOrderByLastScanTimestampAsc();

        if (collectionOpt.isEmpty()) {
            log.debug("Нет активных коллекций для сканирования");
            return;
        }

        CollectionRegistryDocument col = collectionOpt.get();
        processCollection(col);
    }

    private void processCollection(CollectionRegistryDocument col) {
        try {
            String currentCursor = col.getLastHistoryCursor();

            CollectionHistoryDto response = apiClient.getCollectionHistory(
                    col.getAddress(),
                    100,
                    currentCursor,
                    MINT_TYPE,
                    false
            );

            if (response == null || !response.isSuccess()) {
                log.warn("API fail for {}", col.getName());
                updateTimestampOnly(col);
                return;
            }

            var items = response.getResponse().getItems();

            if (items.isEmpty()) {
                log.info("История коллекции {} полностью просканирована. Сброс курсора.", col.getName());
                col.setLastHistoryCursor(null);
                updateTimestampOnly(col);
                return;
            }

            int count = 0;
            for (var dto : items) {
                if (dto.getAddress() != null) {
                    ItemRegistryDocument item = ItemRegistryDocument.builder()
                            .address(dto.getAddress())
                            .collectionAddress(col.getAddress())
                            .name(dto.getName())
                            .mintedAt(Instant.ofEpochMilli(dto.getTimestamp()))
                            .lastSeenAt(Instant.now())
                            .build();
                    itemRepo.save(item);
                    count++;
                }
            }

            col.setLastHistoryCursor(response.getResponse().getCursor());
            col.setLastScanTimestamp(System.currentTimeMillis());
            colRepo.save(col);

            log.info("Saved {} items for {}. Cursor updated.", count, col.getName());

        } catch (Exception e) {
            log.error("Error indexing {}: {}", col.getName(), e.getMessage());
            updateTimestampOnly(col);
        }
    }

    private void updateTimestampOnly(CollectionRegistryDocument col) {
        col.setLastScanTimestamp(System.currentTimeMillis());
        colRepo.save(col);
    }
}