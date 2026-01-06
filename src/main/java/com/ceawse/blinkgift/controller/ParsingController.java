package com.ceawse.blinkgift.controller;

import com.ceawse.blinkgift.worker.BackfillWorker;
import com.ceawse.blinkgift.worker.RealtimeStreamWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/parsing")
@RequiredArgsConstructor
public class ParsingController {

    private final BackfillWorker backfillWorker;
    private final RealtimeStreamWorker realtimeStreamWorker;

    // 1. Кнопка "Запустить выкачку истории"
    // Вызываешь один раз, когда разворачиваешь проект
    @PostMapping("/backfill/start")
    public ResponseEntity<String> startBackfill() {
        // Запускаем в отдельном потоке, чтобы не блокировать ответ сервера
        backfillWorker.runBackfill();

        return ResponseEntity.ok("Backfill process finished!");
    }

    // 2. Кнопка "Принудительно дёрнуть реалтайм" (для отладки)
    // Вообще он работает сам, но иногда полезно пнуть вручную
    @PostMapping("/realtime/force")
    public ResponseEntity<String> forceRealtime() {
        realtimeStreamWorker.pollNewEvents();
        return ResponseEntity.ok("Realtime poll executed.");
    }
}