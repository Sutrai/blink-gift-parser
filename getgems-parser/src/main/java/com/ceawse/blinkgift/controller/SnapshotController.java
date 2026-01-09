package com.ceawse.blinkgift.controller;

import com.ceawse.blinkgift.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/snapshot")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;

    @PostMapping("/{marketplace}")
    public ResponseEntity<String> startSnapshot(@PathVariable String marketplace) {
        if ("getgems".equalsIgnoreCase(marketplace)) {
            snapshotService.runSnapshot(marketplace);
            return ResponseEntity.ok("Snapshot started for GetGems");
        }
        return ResponseEntity.badRequest().body("Marketplace not supported");
    }
}