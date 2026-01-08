package com.ceawse.giftdiscovery.repository;

import com.ceawse.giftdiscovery.model.ProcessorState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessorStateRepository extends MongoRepository<ProcessorState, String> {
}