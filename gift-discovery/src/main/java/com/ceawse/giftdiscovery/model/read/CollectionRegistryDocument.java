package com.ceawse.giftdiscovery.model.read;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "registry_collections")
public class CollectionRegistryDocument {
    @Id
    private String address;
    private String name;
}