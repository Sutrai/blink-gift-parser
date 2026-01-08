package com.ceawse.onchainindexer.worker;

import com.ceawse.onchainindexer.client.RegistryApiClient;
import com.ceawse.onchainindexer.dto.CollectionsListDto;
import com.ceawse.onchainindexer.model.CollectionRegistryDocument;
import com.ceawse.onchainindexer.repository.CollectionRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoveryWorker {

    private final RegistryApiClient apiClient;
    private final CollectionRegistryRepository repository;

    @Scheduled(fixedDelay = 3600000)
    public void discoverCollections() {
        log.info("Starting Collection Discovery...");
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                CollectionsListDto response = apiClient.getCollections(100, cursor);
                if (response == null || !response.isSuccess() || response.getResponse().getItems().isEmpty()) {
                    hasMore = false;
                    continue;
                }

                var items = response.getResponse().getItems();
                for (var dto : items) {

                    if (!repository.existsById(dto.getAddress())) {
                        CollectionRegistryDocument doc = CollectionRegistryDocument.builder()
                                .address(dto.getAddress())
                                .name(dto.getName())
                                .ownerAddress(dto.getOwnerAddress())
                                .enabled(true)
                                .build();
                        repository.save(doc);
                        log.info("Discovered new collection: {}", dto.getName());
                    }
                }

                cursor = response.getResponse().getCursor();
                if (cursor == null) hasMore = false;

            } catch (Exception e) {
                log.error("Discovery error", e);
                hasMore = false;
            }
        }
    }
}