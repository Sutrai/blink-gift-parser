package com.ceawse.coreprocessor.service.impl;

import com.ceawse.coreprocessor.model.CurrentSaleDocument;
import com.ceawse.coreprocessor.model.GiftHistoryDocument;
import com.ceawse.coreprocessor.model.SoldGiftDocument;
import com.ceawse.coreprocessor.repository.CurrentSaleRepository;
import com.ceawse.coreprocessor.repository.SoldGiftRepository;
import com.ceawse.coreprocessor.service.MarketStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketStateMachineImpl implements MarketStateMachine {

    private final CurrentSaleRepository currentSaleRepository;
    private final SoldGiftRepository soldGiftRepository;

    /**
     * Применяет одно событие к состоянию рынка.
     */
    @Transactional
    public void applyEvent(GiftHistoryDocument event) {
        String type = event.getEventType().toLowerCase();

        switch (type) {
            case "putupforsale":
                handleList(event);
                break;
            case "cancelsale":
                handleUnlist(event);
                break;
            case "sold":
                handleSold(event);
                break;
            default:
                // Остальные типы (mint, transfer) нас пока не волнуют для витрины
                break;
        }
    }

    private void handleList(GiftHistoryDocument event) {
        // Конвертируем цену из строки в Long для сортировки
        Long priceNano = parseNano(event.getPriceNano());

        // Проверяем, есть ли уже запись
        CurrentSaleDocument sale = currentSaleRepository.findByAddress(event.getAddress())
                .orElse(CurrentSaleDocument.builder()
                        .address(event.getAddress())
                        .collectionAddress(event.getCollectionAddress())
                        .name(event.getName())
                        .isOffchain(Boolean.TRUE.equals(event.getIsOffchain()))
                        .listedAt(Instant.ofEpochMilli(event.getTimestamp()))
                        .build());

        // Обновляем данные (Upsert)
        sale.setPrice(event.getPrice());
        sale.setPriceNano(priceNano);
        sale.setCurrency(event.getCurrency());
        sale.setSeller(event.getOldOwner()); // Тот, кто выставил (oldOwner в контексте листинга)
        sale.setUpdatedAt(Instant.ofEpochMilli(event.getTimestamp()));

        currentSaleRepository.save(sale);
        log.debug("List applied: {} for {} TON", event.getAddress(), event.getPrice());
    }

    private void handleUnlist(GiftHistoryDocument event) {
        // Если сняли с продажи — просто удаляем из витрины
        currentSaleRepository.deleteByAddress(event.getAddress());
        log.debug("Unlist applied: {}", event.getAddress());
    }

    private void handleSold(GiftHistoryDocument event) {
        // 1. Удаляем из активных продаж (его больше нельзя купить)
        currentSaleRepository.deleteByAddress(event.getAddress());

        // 2. Добавляем в историю продаж
        Long priceNano = parseNano(event.getPriceNano());

        SoldGiftDocument sold = SoldGiftDocument.builder()
                .address(event.getAddress())
                .collectionAddress(event.getCollectionAddress())
                .name(event.getName())
                .price(event.getPrice())
                .priceNano(priceNano)
                .currency(event.getCurrency())
                .seller(event.getOldOwner())
                .buyer(event.getNewOwner())
                .soldAt(Instant.ofEpochMilli(event.getTimestamp()))
                .isOffchain(Boolean.TRUE.equals(event.getIsOffchain()))
                .build();

        soldGiftRepository.save(sold);
        log.info("SALE! {} sold for {} TON", event.getName(), event.getPrice());
    }

    // Безопасный парсинг нанотонов
    private Long parseNano(String nanoStr) {
        if (nanoStr == null) return 0L;
        try {
            return Long.parseLong(nanoStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}