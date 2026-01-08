package com.ceawse.blinkgift.repository;

import com.blinkgift.parser.getgems.model.IngestionState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IngestionStateRepository extends MongoRepository<IngestionState, String> {
}