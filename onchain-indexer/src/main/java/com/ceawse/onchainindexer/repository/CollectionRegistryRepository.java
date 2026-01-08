package com.ceawse.onchainindexer.repository;

import com.ceawse.onchainindexer.model.CollectionRegistryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface CollectionRegistryRepository extends MongoRepository<CollectionRegistryDocument, String> {

    Optional<CollectionRegistryDocument> findFirstByEnabledTrueOrderByLastScanTimestampAsc();
}