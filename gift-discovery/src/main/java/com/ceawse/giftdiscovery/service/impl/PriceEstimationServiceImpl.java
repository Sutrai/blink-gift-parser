package com.ceawse.giftdiscovery.service.impl;

import com.ceawse.giftdiscovery.dto.MarketAttributeDataDto;
import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.service.MarketDataService;
import com.ceawse.giftdiscovery.service.PriceEstimationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceEstimationServiceImpl implements PriceEstimationService {

    private final MongoTemplate mongoTemplate;
    private final MarketDataService marketDataService;

    private static final int BATCH_SIZE = 1000;
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    @Override
    public void estimateGiftPrices() {
        Query query = new Query(Criteria.where("attributes").exists(true)
                .and("marketData.estimatedPrice").exists(false))
                .limit(BATCH_SIZE);

        List<UniqueGiftDocument> gifts = mongoTemplate.find(query, UniqueGiftDocument.class);

        if (gifts.isEmpty()) return;

        log.info("Estimating prices for {} gifts...", gifts.size());

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UniqueGiftDocument.class);
        int updatesCount = 0;

        for (UniqueGiftDocument gift : gifts) {
            String collectionAddress = marketDataService.resolveCollectionAddress(gift.getName(), gift.getCollectionAddress());
            if (collectionAddress == null) continue;

            BigDecimal floorPrice = marketDataService.getCollectionFloor(collectionAddress);
            UniqueGiftDocument.GiftAttributes attrs = gift.getAttributes();

            enrichAttributeMarketData(attrs, collectionAddress);

            BigDecimal estimatedPrice = calculatePrice(floorPrice, attrs);

            Update update = buildUpdate(collectionAddress, gift.getCollectionAddress(), attrs, floorPrice, estimatedPrice);

            bulkOps.updateOne(new Query(Criteria.where("_id").is(gift.getId())), update);
            updatesCount++;
        }

        if (updatesCount > 0) {
            bulkOps.execute();
            log.info("Price estimation completed. Updated: {}", updatesCount);
        }
    }

    private void enrichAttributeMarketData(UniqueGiftDocument.GiftAttributes attrs, String collectionAddress) {
        updateAttr(collectionAddress, "Model", attrs.getModel(),
                attrs::setModelPrice, attrs::setModelRarityCount);
        updateAttr(collectionAddress, "Backdrop", attrs.getBackdrop(),
                attrs::setBackdropPrice, attrs::setBackdropRarityCount);
        updateAttr(collectionAddress, "Symbol", attrs.getSymbol(),
                attrs::setSymbolPrice, attrs::setSymbolRarityCount);
    }

    private void updateAttr(String colAddr, String type, String val,
                            java.util.function.Consumer<BigDecimal> setPrice,
                            java.util.function.Consumer<Integer> setCount) {
        var data = marketDataService.getAttributeData(colAddr, type, val);
        if (data != null) {
            setPrice.accept(data.getPrice());
            setCount.accept(data.getCount());
        }
    }

    private BigDecimal calculatePrice(BigDecimal floor, UniqueGiftDocument.GiftAttributes attrs) {
        BigDecimal modelP = attrs.getModelPrice() != null ? attrs.getModelPrice() : floor;
        BigDecimal backdropP = attrs.getBackdropPrice() != null ? attrs.getBackdropPrice() : floor;

        // Formula: (floor * 2 + model + backdrop) / 4
        return floor.multiply(TWO)
                .add(modelP)
                .add(backdropP)
                .divide(FOUR, 2, RoundingMode.HALF_UP);
    }

    private Update buildUpdate(String newColAddress, String oldColAddress,
                               UniqueGiftDocument.GiftAttributes attrs,
                               BigDecimal floor, BigDecimal estimated) {
        Update update = new Update();

        if (!newColAddress.equals(oldColAddress)) {
            update.set("collectionAddress", newColAddress);
        }

        update.set("attributes.modelPrice", attrs.getModelPrice());
        update.set("attributes.modelRarityCount", attrs.getModelRarityCount());
        update.set("attributes.backdropPrice", attrs.getBackdropPrice());
        update.set("attributes.backdropRarityCount", attrs.getBackdropRarityCount());
        update.set("attributes.symbolPrice", attrs.getSymbolPrice());
        update.set("attributes.symbolRarityCount", attrs.getSymbolRarityCount());

        update.set("marketData.collectionFloorPrice", floor);
        update.set("marketData.estimatedPrice", estimated);
        update.set("marketData.priceUpdatedAt", Instant.now());

        return update;
    }
}