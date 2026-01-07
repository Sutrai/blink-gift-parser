package com.ceawse.coreprocessor.repository;

import com.ceawse.coreprocessor.model.CurrentSaleDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;


public interface CurrentSaleRepository extends MongoRepository<CurrentSaleDocument, String> {
    Optional<CurrentSaleDocument> findByAddress(String address);

    void deleteByAddress(String address);
}