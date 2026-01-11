package com.ceawse.giftdiscovery.controller;

import com.ceawse.giftdiscovery.model.UniqueGiftDocument;
import com.ceawse.giftdiscovery.repository.UniqueGiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/gifts")
@RequiredArgsConstructor
public class ExternalGiftController {

    private final UniqueGiftRepository uniqueGiftRepository;
    private final MongoTemplate mongoTemplate; // Добавляем MongoTemplate для гибкого поиска

    @GetMapping("/search")
    public ResponseEntity<UniqueGiftDocument> getGiftByName(@RequestParam String name) {
        // 1. Сначала пробуем найти точное совпадение (всегда лучше для производительности)
        Optional<UniqueGiftDocument> gift = uniqueGiftRepository.findByName(name);

        if (gift.isEmpty()) {
            // 2. Если не нашли, строим безопасную регулярку вручную
            // Для "MiniOscar #2727" она превратится в "^M\s*i\s*n\s*i\s*O\s*s\s*c\s*a\s*r\s*#\s*2\s*7\s*2\s*7$"
            StringBuilder regexBuilder = new StringBuilder("^");
            for (char c : name.toCharArray()) {
                if (Character.isWhitespace(c)) continue; // Пропускаем пробелы, которые пришли в запросе

                // Экранируем символ (Pattern.quote) и добавляем возможность пробела после него
                regexBuilder.append(java.util.regex.Pattern.quote(String.valueOf(c)))
                        .append("\\s*");
            }
            regexBuilder.append("$");

            String safeRegex = regexBuilder.toString();

            // Используем MongoTemplate для поиска по этой регулярке
            Query query = new Query(Criteria.where("name").regex(safeRegex, "i"));
            gift = Optional.ofNullable(mongoTemplate.findOne(query, UniqueGiftDocument.class));
        }

        return gift.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}