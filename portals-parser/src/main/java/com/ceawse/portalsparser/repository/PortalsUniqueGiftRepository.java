package com.ceawse.portalsparser.repository;

import com.ceawse.portalsparser.domain.UniqueGiftDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PortalsUniqueGiftRepository extends MongoRepository<UniqueGiftDocument, String> {
}