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

    @Scheduled(fixedDelay = 1000) // Пауза 1.5 сек между запросами
    public void indexNextBatch() {
        // 1. Берем коллекцию из БД, которую давно не трогали (или новую)
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

            // 2. Запрос к API
            CollectionHistoryDto response = apiClient.getCollectionHistory(
                    col.getAddress(),
                    100,
                    currentCursor,
                    MINT_TYPE,
                    false
            );

            // Если API вернул ошибку или пустоту
            if (response == null || !response.isSuccess()) {
                log.warn("API fail for {}", col.getName());
                // Обновляем время, чтобы не долбить её бесконечно, а перейти к следующей
                updateTimestampOnly(col);
                return;
            }

            var items = response.getResponse().getItems();

            // 3. Если список пуст — значит, мы дочитали историю до конца.
            if (items.isEmpty()) {
                log.info("История коллекции {} полностью просканирована. Сброс курсора.", col.getName());
                // Сбрасываем курсор в null, чтобы в СЛЕДУЮЩИЙ РАЗ начать искать новые mints с начала (или конца, смотря как API работает)
                col.setLastHistoryCursor(null);
                updateTimestampOnly(col);
                return;
            }

            // 4. Сохраняем элементы (Upsert)
            int count = 0;
            for (var dto : items) {
                if (dto.getAddress() != null) {
                    ItemRegistryDocument item = ItemRegistryDocument.builder()
                            .address(dto.getAddress()) // ID - уникальный адрес
                            .collectionAddress(col.getAddress())
                            .name(dto.getName())
                            .mintedAt(Instant.ofEpochMilli(dto.getTimestamp()))
                            .lastSeenAt(Instant.now())
                            .build();
                    itemRepo.save(item);
                    count++;
                }
            }

            // 5. Сохраняем новый курсор и время
            // Теперь эта коллекция станет "свежей" и уйдет в конец очереди сортировки
            col.setLastHistoryCursor(response.getResponse().getCursor());
            col.setLastScanTimestamp(System.currentTimeMillis());
            colRepo.save(col);

            log.info("Saved {} items for {}. Cursor updated.", count, col.getName());

        } catch (Exception e) {
            log.error("Error indexing {}: {}", col.getName(), e.getMessage());
            // При ошибке тоже обновляем время, чтобы дать шанс другим коллекциям
            updateTimestampOnly(col);
        }
    }

    private void updateTimestampOnly(CollectionRegistryDocument col) {
        col.setLastScanTimestamp(System.currentTimeMillis());
        colRepo.save(col);
    }
}