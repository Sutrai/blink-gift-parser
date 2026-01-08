package com.ceawse.onchainindexer.repository;

import com.ceawse.onchainindexer.model.ItemRegistryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ItemRegistryRepository extends MongoRepository<ItemRegistryDocument, String> {

    Optional<ItemRegistryDocument> findByNameAndCollectionAddress(String name, String collectionAddress);
}