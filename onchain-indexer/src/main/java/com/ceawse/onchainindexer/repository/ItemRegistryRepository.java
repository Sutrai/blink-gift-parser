package com.ceawse.onchainindexer.repository;

import com.ceawse.onchainindexer.model.ItemRegistryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ItemRegistryRepository extends MongoRepository<ItemRegistryDocument, String> {

    /**
     * Найти подарок по имени внутри конкретной коллекции.
     * Это самый важный метод для матчинга (Resolving).
     * Когда парсер видит "Dog #555" в листинге, он вызывает этот метод,
     * чтобы узнать реальный ончейн-адрес подарка.
     */
    Optional<ItemRegistryDocument> findByNameAndCollectionAddress(String name, String collectionAddress);

    // Также можно искать просто по адресу, если он известен (стандартный findById)
}