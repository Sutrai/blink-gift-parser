package com.ceawse.blinkgift.worker;

import com.ceawse.blinkgift.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetGemsRealtimeParser {
    private final HistoryService historyService;

    @Scheduled(fixedDelayString = "${getgems.scheduler.realtime:3000}")
    public void poll() {
        historyService.fetchRealtimeEvents();
    }
}