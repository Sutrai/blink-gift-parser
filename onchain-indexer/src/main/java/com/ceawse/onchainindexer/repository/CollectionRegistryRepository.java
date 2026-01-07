package com.ceawse.onchainindexer.repository;

import com.ceawse.onchainindexer.model.CollectionRegistryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface CollectionRegistryRepository extends MongoRepository<CollectionRegistryDocument, String> {

    /**
     * Находит ОДНУ коллекцию, которую давно не сканировали.
     * Сортировка по возрастанию (Asc): null -> старые даты -> новые даты.
     * Те, у кого null (новые), попадут в начало очереди.
     */
    Optional<CollectionRegistryDocument> findFirstByEnabledTrueOrderByLastScanTimestampAsc();
}