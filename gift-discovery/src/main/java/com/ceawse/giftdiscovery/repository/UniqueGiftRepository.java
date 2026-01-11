package com.ceawse.giftdiscovery.repository;

import com.ceawse.giftdiscovery.model.GiftHistoryDocument;
import com.ceawse.giftdiscovery.model.ItemRegistryDocument;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UniqueGiftRepository extends MongoRepository<UniqueGiftDocument, String>, CustomUniqueGiftRepository {

    Optional<UniqueGiftDocument> findByName(String name);

}
