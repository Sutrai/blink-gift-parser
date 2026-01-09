package com.ceawse.coreprocessor.service.impl;

import com.ceawse.coreprocessor.service.MessageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageProvider {

    private static final Locale DEFAULT_LOCALE = new Locale("ru", "RU");
    private final MessageSource messageSource;

    @Override
    public String getMessage(String code) {
        return getMessage(code, DEFAULT_LOCALE);
    }

    @Override
    public String getMessage(String code, Object... args) {
        try {
            return messageSource.getMessage(code, args, DEFAULT_LOCALE);
        } catch (NoSuchMessageException e) {
            log.warn("Message code not found: {}", code);
            return code;
        }
    }

    @Override
    public String getMessage(String code, Locale locale) {
        try {
            return messageSource.getMessage(code, null, locale != null ? locale : DEFAULT_LOCALE);
        } catch (NoSuchMessageException e) {
            return code;
        }
    }
}