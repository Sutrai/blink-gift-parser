package com.ceawse.blinkgift.repository;

import com.ceawse.blinkgift.domain.UniqueGiftDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GetGemsUniqueGiftRepository extends MongoRepository<UniqueGiftDocument, String> {
}