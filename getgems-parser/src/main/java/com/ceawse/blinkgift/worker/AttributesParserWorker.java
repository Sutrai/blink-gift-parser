package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttributesParserWorker {
    private final CollectionService collectionService;

    @Scheduled(fixedDelayString = "${getgems.scheduler.attributes:60000}")
    public void parseAttributes() {
        collectionService.updateAllCollectionsAttributes();
    }
}