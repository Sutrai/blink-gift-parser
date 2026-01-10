package com.ceawse.giftdiscovery.worker;

import com.ceawse.giftdiscovery.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscoveryScheduler {

    private final DiscoveryService discoveryService;
}