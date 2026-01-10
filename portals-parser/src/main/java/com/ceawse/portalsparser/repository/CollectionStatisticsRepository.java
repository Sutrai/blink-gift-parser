package com.ceawse.portalsparser.repository;

import com.ceawse.portalsparser.domain.CollectionStatisticsDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CollectionStatisticsRepository extends MongoRepository<CollectionStatisticsDocument, String> {
}