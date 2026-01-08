package com.ceawse.blinkgift.repository;

import com.ceawse.blinkgift.domain.CollectionStatisticsDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CollectionStatisticsRepository extends MongoRepository<CollectionStatisticsDocument, String> {
}