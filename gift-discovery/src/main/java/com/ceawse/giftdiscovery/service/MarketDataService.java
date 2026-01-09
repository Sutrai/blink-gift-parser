package com.ceawse.giftdiscovery.service;

import com.ceawse.giftdiscovery.dto.MarketAttributeDataDto;
import java.math.BigDecimal;

public interface MarketDataService {
    void refreshCache();
    String resolveCollectionAddress(String giftName, String providedAddress);
    BigDecimal getCollectionFloor(String collectionAddress);
    MarketAttributeDataDto getAttributeData(String collectionAddress, String traitType, String value);
}