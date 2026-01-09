package com.ceawse.portalsparser.repository;

import com.ceawse.portalsparser.domain.PortalsIngestionState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PortalsIngestionStateRepository extends MongoRepository<PortalsIngestionState, String> {
}