package com.example.fitnessbot.model;

import java.util.Locale;
import java.util.Optional;

public enum UserLanguage {
    ENGLISH("en"),
    RUSSIAN("ru");

    private final String code;

    UserLanguage(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static Optional<UserLanguage> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "en", "eng", "english" -> Optional.of(ENGLISH);
            case "ru", "rus", "russian", "русский", "рус" -> Optional.of(RUSSIAN);
            default -> Optional.empty();
        };
    }
}
