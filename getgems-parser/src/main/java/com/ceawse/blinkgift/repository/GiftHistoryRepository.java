package com.ceawse.blinkgift.repository;

import com.ceawse.blinkgift.domain.GiftHistoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GiftHistoryRepository extends MongoRepository<GiftHistoryDocument, String> {
    boolean existsByHash(String hash);
}