package com.ceawse.portalsparser.worker;

import com.ceawse.portalsparser.service.impl.PortalsCollectionDataServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortalsDataParserWorker {
    private final PortalsCollectionDataServiceImpl dataService;

    // Раз в минуту обновляем атрибуты
    @Scheduled(fixedDelayString = "${portals.scheduler.attributes:60000}")
    public void parseAttributes() {
        dataService.updateAllPortalsAttributes();
    }

    // Раз в 30 секунд обновляем общую статистику (флор)
    @Scheduled(fixedDelayString = "${portals.scheduler.stats:30000}")
    public void parseStatistics() {
        dataService.updateAllPortalsStatistics();
    }
}