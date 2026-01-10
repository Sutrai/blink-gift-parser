package com.ceawse.portalsparser.repository;

import com.ceawse.portalsparser.domain.CollectionAttributeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CollectionAttributeRepository extends MongoRepository<CollectionAttributeDocument, String> {
}
