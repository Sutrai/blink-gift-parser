package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatisticsParserWorker {
    private final CollectionService collectionService;

    @Scheduled(fixedDelayString = "${getgems.scheduler.stats:30000}")
    public void parseStatistics() {
        collectionService.updateAllCollectionsStatistics();
    }
}