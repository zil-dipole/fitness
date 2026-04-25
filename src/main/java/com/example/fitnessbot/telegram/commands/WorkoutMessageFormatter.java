package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.WorkoutService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WorkoutMessageFormatter {

    public static final String START_ACTIVE_DAY_CALLBACK = "start_active_day";
    public static final String SKIP_EXERCISE_CALLBACK = "skip_workout_exercise";
    public static final String FINISH_WORKOUT_CALLBACK = "finish_workout";

    private static final DateTimeFormatter HISTORY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private WorkoutMessageFormatter() {
    }

    public static String formatExerciseView(WorkoutService.WorkoutExerciseView view) {
        StringBuilder response = new StringBuilder();
        response.append("<b>")
                .append(TrainingDayMessageFormatter.escapeHtml(view.trainingDayTitle()))
                .append("</b>\n\n");
        response.append("Exercise ")
                .append(view.exerciseNumber())
                .append("/")
                .append(view.totalExercises())
                .append(": <b>")
                .append(TrainingDayMessageFormatter.escapeHtml(view.exerciseName()))
                .append("</b>\n");
        response.append("Set ").append(view.currentSetNumber()).append("/").append(view.totalSets()).append("\n");

        if (view.repsOrDuration() != null && !view.repsOrDuration().isBlank()) {
            response.append("Reps/Duration: ")
                    .append(TrainingDayMessageFormatter.escapeHtml(view.repsOrDuration()))
                    .append("\n");
        }

        if (view.notes() != null && !view.notes().isBlank()) {
            response.append("Prep/Notes: ")
                    .append(TrainingDayMessageFormatter.escapeHtml(view.notes()))
                    .append("\n");
        }

        if (!view.videoUrls().isEmpty()) {
            response.append("Videos:\n");
            for (String videoUrl : view.videoUrls()) {
                response.append("- ")
                        .append(TrainingDayMessageFormatter.escapeHtml(videoUrl))
                        .append("\n");
            }
        }

        response.append("\n");
        appendHistory(response, view.history());
        response.append("\nSend weight for set ")
                .append(view.currentSetNumber())
                .append(" in kg, for example: 60 or 60.5.");

        return response.toString();
    }

    public static InlineKeyboardMarkup startDayKeyboard() {
        InlineKeyboardButton startButton = new InlineKeyboardButton();
        startButton.setText("Start Day");
        startButton.setCallbackData(START_ACTIVE_DAY_CALLBACK);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(startButton)));
        return markup;
    }

    public static InlineKeyboardMarkup exerciseKeyboard() {
        InlineKeyboardButton skipButton = new InlineKeyboardButton();
        skipButton.setText("Skip Exercise");
        skipButton.setCallbackData(SKIP_EXERCISE_CALLBACK);

        InlineKeyboardButton finishButton = new InlineKeyboardButton();
        finishButton.setText("Finish Day");
        finishButton.setCallbackData(FINISH_WORKOUT_CALLBACK);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(skipButton, finishButton)));
        return markup;
    }

    private static void appendHistory(StringBuilder response, List<WorkoutService.WorkoutHistoryEntry> history) {
        if (history.isEmpty()) {
            response.append("Previous weights: none yet.\n");
            return;
        }

        response.append("Previous weights:\n");
        for (WorkoutService.WorkoutHistoryEntry entry : history) {
            String weights = entry.weights().stream()
                    .map(WorkoutMessageFormatter::formatWeight)
                    .collect(Collectors.joining(" / "));
            response.append("- ")
                    .append(entry.startedAt().format(HISTORY_DATE_FORMAT))
                    .append(": ")
                    .append(weights)
                    .append(" kg\n");
        }
    }

    private static String formatWeight(Double weight) {
        if (weight == null) {
            return "";
        }
        return BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString();
    }
}
