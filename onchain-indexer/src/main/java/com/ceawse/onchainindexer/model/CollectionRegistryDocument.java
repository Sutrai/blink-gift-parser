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
    private String address;

    private String name;
    private String ownerAddress;

    @Builder.Default
    private boolean enabled = true;

    private String lastHistoryCursor;
    private Long lastScanTimestamp;
}