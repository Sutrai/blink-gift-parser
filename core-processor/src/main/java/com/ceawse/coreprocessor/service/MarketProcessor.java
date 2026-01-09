package com.ceawse.coreprocessor.service;

import com.ceawse.coreprocessor.model.GiftHistoryDocument;

public interface MarketProcessor {
    void processEvent(GiftHistoryDocument event);
}