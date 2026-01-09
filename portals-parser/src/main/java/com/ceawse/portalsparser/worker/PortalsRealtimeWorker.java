package com.ceawse.portalsparser.worker;

import com.ceawse.portalsparser.service.PortalsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortalsRealtimeWorker {

    private final PortalsService portalsService;

    @Scheduled(fixedDelay = 3000)
    public void pollEvents() {
        portalsService.processRealtimeEvents();
    }
}