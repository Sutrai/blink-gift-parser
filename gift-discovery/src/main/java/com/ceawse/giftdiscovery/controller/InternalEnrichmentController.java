package com.ceawse.giftdiscovery.controller;

import com.ceawse.giftdiscovery.dto.internal.EnrichmentRequest;
import com.ceawse.giftdiscovery.service.impl.EnrichmentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InternalEnrichmentController {

    private final EnrichmentTaskService enrichmentTaskService;

    @PostMapping("/internal/v1/enrichment/calculate")
    @ResponseStatus(HttpStatus.ACCEPTED) // Возвращаем 202 Accepted (задача принята)
    public void enrich(@RequestBody EnrichmentRequest request) {
        if (request == null || request.getId() == null) {
            return;
        }
        enrichmentTaskService.processEnrichmentAsync(request);
    }
}