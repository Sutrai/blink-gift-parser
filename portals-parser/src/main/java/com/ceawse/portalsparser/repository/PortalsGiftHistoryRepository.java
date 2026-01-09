package com.ceawse.portalsparser.repository;

import com.ceawse.portalsparser.domain.PortalsGiftHistoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PortalsGiftHistoryRepository extends MongoRepository<PortalsGiftHistoryDocument, String> {
    boolean existsByHash(String hash);
}