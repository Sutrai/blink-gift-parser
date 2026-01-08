package com.ceawse.giftdiscovery.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document(collection = "registry_items")
public class ItemRegistryDocument {
    @Id
    private String address;

    @Indexed
    private String collectionAddress;

    @Indexed
    private String name;

    private Instant mintedAt;
    private Instant lastSeenAt;
}