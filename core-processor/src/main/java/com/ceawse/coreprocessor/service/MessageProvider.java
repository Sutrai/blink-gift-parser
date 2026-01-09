package com.ceawse.coreprocessor.service;

import java.util.Locale;

public interface MessageProvider {
    String getMessage(String code);
    String getMessage(String code, Object... args);
    String getMessage(String code, Locale locale);
}