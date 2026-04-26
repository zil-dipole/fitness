package com.example.fitnessbot.parser;

import java.util.Locale;

public final class TrainingDayTitleNormalizer {

    private static final String LATIN_LAYOUT = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM";
    private static final String RUSSIAN_LAYOUT = "йцукенгшщзфывапролдячсмитьЙЦУКЕНГШЩЗФЫВАПРОЛДЯЧСМИТЬ";

    private TrainingDayTitleNormalizer() {
    }

    public static String normalize(String title) {
        if (title == null) {
            return null;
        }

        String trimmed = title.trim();
        if (trimmed.isBlank() || looksLikeTrainingDayTitle(trimmed)) {
            return trimmed;
        }

        String converted = convertLatinKeyboardToRussian(trimmed);
        if (!converted.equals(trimmed) && looksLikeTrainingDayTitle(converted)) {
            return converted;
        }

        return trimmed;
    }

    public static boolean looksLikeTrainingDayTitle(String title) {
        if (title == null) {
            return false;
        }

        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("трен") || lower.contains("training") || lower.contains("workout");
    }

    private static String convertLatinKeyboardToRussian(String text) {
        StringBuilder converted = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int layoutIndex = LATIN_LAYOUT.indexOf(ch);
            converted.append(layoutIndex >= 0 ? RUSSIAN_LAYOUT.charAt(layoutIndex) : ch);
        }
        return converted.toString();
    }
}
