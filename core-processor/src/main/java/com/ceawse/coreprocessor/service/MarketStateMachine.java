package com.ceawse.coreprocessor.service;

import com.ceawse.coreprocessor.model.GiftHistoryDocument;

public interface MarketStateMachine {
    void applyEvent(GiftHistoryDocument event);
}
