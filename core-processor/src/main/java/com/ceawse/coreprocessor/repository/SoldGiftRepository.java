package com.ceawse.coreprocessor.repository;

import com.ceawse.coreprocessor.model.SoldGiftDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SoldGiftRepository extends MongoRepository<SoldGiftDocument, String> {
}
