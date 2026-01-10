package com.ceawse.onchainindexer.repository;

import com.ceawse.onchainindexer.model.UniqueGiftDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UniqueGiftRepository extends MongoRepository<UniqueGiftDocument, String> {
}