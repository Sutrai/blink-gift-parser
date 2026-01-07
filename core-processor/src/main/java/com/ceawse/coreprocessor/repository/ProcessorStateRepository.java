package com.ceawse.coreprocessor.repository;

import com.ceawse.coreprocessor.model.ProcessorState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessorStateRepository extends MongoRepository<ProcessorState, String> {
}
