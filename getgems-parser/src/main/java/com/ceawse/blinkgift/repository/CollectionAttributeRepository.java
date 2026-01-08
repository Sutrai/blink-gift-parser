package com.ceawse.blinkgift.repository;

import com.ceawse.blinkgift.domain.CollectionAttributeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CollectionAttributeRepository extends MongoRepository<CollectionAttributeDocument, String> {
}
