package com.ceawse.onchainindexer.controller;

import com.ceawse.onchainindexer.model.CollectionRegistryDocument;
import com.ceawse.onchainindexer.repository.CollectionRegistryRepository;
import com.ceawse.onchainindexer.worker.IndexerWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controller/InternalController.java
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalController {

    private final CollectionRegistryRepository collectionRepository;
    private final IndexerWorker indexerWorker; // Инжектим воркер

    @GetMapping("/collections")
    public List<CollectionRegistryDocument> getAllCollections() {
        return collectionRepository.findAll();
    }

    // НОВЫЙ МЕТОД: Запуск парсинга ончейн данных по запросу
    @PostMapping("/onchain/run")
    public ResponseEntity<String> runOnchainParsing() {
        indexerWorker.runFullIndexing(); // Вызов асинхронного метода
        return ResponseEntity.ok("Onchain indexing task triggered in background");
    }
}