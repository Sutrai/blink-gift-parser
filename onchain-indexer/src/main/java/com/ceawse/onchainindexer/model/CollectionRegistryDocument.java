package com.ceawse.onchainindexer.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document(collection = "registry_collections")
public class CollectionRegistryDocument {
    @Id
    private String address; // Primary Key

    private String name;
    private String ownerAddress;

    // Флаг управления: хотим ли мы тратить ресурсы на эту коллекцию
    @Builder.Default
    private boolean enabled = true;

    // Состояние индексации (курсор истории)
    private String lastHistoryCursor;
    private Long lastScanTimestamp;
}