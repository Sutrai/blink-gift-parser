package com.ceawse.portalsparser.controller;

import com.ceawse.portalsparser.service.PortalsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portals")
@RequiredArgsConstructor
public class PortalsController {

    private final PortalsService portalsService;

    @PostMapping("/snapshot")
    public ResponseEntity<String> startSnapshot() {
        portalsService.runSnapshot();
        return ResponseEntity.ok("Portals snapshot started");
    }
}