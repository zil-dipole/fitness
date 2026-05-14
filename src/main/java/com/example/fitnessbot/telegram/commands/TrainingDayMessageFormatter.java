package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.UserLanguage;

final class TrainingDayMessageFormatter {

    private TrainingDayMessageFormatter() {
    }

    static String format(TrainingDay trainingDay) {
        return format(trainingDay, UserLanguage.ENGLISH);
    }

    static String format(TrainingDay trainingDay, UserLanguage language) {
        StringBuilder response = new StringBuilder();

        if (trainingDay.getRawText() != null && !trainingDay.getRawText().isBlank()) {
            response.append("<blockquote>")
                    .append(escapeHtml(trainingDay.getRawText().strip()))
                    .append("</blockquote>\n\n");
        }

        response.append("<b>").append(escapeHtml(trainingDay.getTitle())).append("</b>\n\n");

        if (trainingDay.getExercises() != null && !trainingDay.getExercises().isEmpty()) {
            response.append(BotText.trainingDayExercisesLabel(language)).append("\n");
            for (int i = 0; i < trainingDay.getExercises().size(); i++) {
                Exercise exercise = trainingDay.getExercises().get(i);
                response.append(i + 1).append(". ").append(escapeHtml(exercise.getName())).append("\n");

                if (exercise.getSets() != null && exercise.getRepsOrDuration() != null) {
                    response.append("   ")
                            .append(exercise.getSets())
                            .append(" x ")
                            .append(escapeHtml(exercise.getRepsOrDuration()));
                } else if (exercise.getRepsOrDuration() != null) {
                    response.append("   ").append(escapeHtml(exercise.getRepsOrDuration()));
                }

                if (exercise.getLastWeightKg() != null) {
                    response.append(" @ ").append(exercise.getLastWeightKg()).append(" kg");
                }

                response.append("\n");

                if (exercise.getNotes() != null && !exercise.getNotes().isEmpty()) {
                    response.append("   ")
                            .append(BotText.trainingDayNotesLabel(language))
                            .append(escapeHtml(exercise.getNotes()))
                            .append("\n");
                }

                if (exercise.getVideoUrls() != null && !exercise.getVideoUrls().isEmpty()) {
                    response.append("   ").append(BotText.trainingDayVideosLabel(language)).append("\n");
                    for (String url : exercise.getVideoUrls()) {
                        response.append("   - ").append(escapeHtml(url)).append("\n");
                    }
                }

                response.append("\n");
            }
        } else {
            response.append(BotText.trainingDayNoExercises(language)).append("\n");
        }

        return response.toString();
    }

    static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }
}
