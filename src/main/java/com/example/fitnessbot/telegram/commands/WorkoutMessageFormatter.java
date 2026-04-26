package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.WorkoutService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WorkoutMessageFormatter {

    public static final String START_ACTIVE_DAY_CALLBACK = "start_active_day";
    public static final String PREVIOUS_WEIGHT_CALLBACK = "previous_weight";
    public static final String NO_LOAD_CALLBACK = "none_load";
    public static final String SKIP_EXERCISE_CALLBACK = "skip_workout_exercise";
    public static final String FINISH_WORKOUT_CALLBACK = "finish_workout";

    private static final DateTimeFormatter HISTORY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private WorkoutMessageFormatter() {
    }

    public static String formatExerciseResult(String message, WorkoutService.WorkoutExerciseView view) {
        return TrainingDayMessageFormatter.escapeHtml(message) + "\n\n" + formatExerciseView(view);
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
        response.append("\nSend load for set ")
                .append(view.currentSetNumber())
                .append(". Examples: 60, red band, bodyweight. Send none for no load.");

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
        return exerciseKeyboard(null);
    }

    public static InlineKeyboardMarkup exerciseKeyboard(WorkoutService.WorkoutExerciseView view) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (view != null && view.previousWeightKg() != null && view.previousWeightKg() > 0) {
            InlineKeyboardButton previousWeightButton = new InlineKeyboardButton();
            previousWeightButton.setText("Use " + formatWeight(view.previousWeightKg()) + " kg");
            previousWeightButton.setCallbackData(PREVIOUS_WEIGHT_CALLBACK);
            rows.add(List.of(previousWeightButton));
        }

        InlineKeyboardButton noLoadButton = new InlineKeyboardButton();
        noLoadButton.setText("No Load");
        noLoadButton.setCallbackData(NO_LOAD_CALLBACK);

        InlineKeyboardButton skipButton = new InlineKeyboardButton();
        skipButton.setText("Skip Exercise");
        skipButton.setCallbackData(SKIP_EXERCISE_CALLBACK);

        InlineKeyboardButton finishButton = new InlineKeyboardButton();
        finishButton.setText("Finish Day");
        finishButton.setCallbackData(FINISH_WORKOUT_CALLBACK);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        rows.add(List.of(noLoadButton));
        rows.add(List.of(skipButton, finishButton));
        markup.setKeyboard(rows);
        return markup;
    }

    private static void appendHistory(StringBuilder response, List<WorkoutService.WorkoutHistoryEntry> history) {
        if (history.isEmpty()) {
            response.append("Previous loads: none yet.\n");
            return;
        }

        response.append("Previous loads:\n");
        for (WorkoutService.WorkoutHistoryEntry entry : history) {
            String loads = entry.loads().stream()
                    .map(WorkoutMessageFormatter::formatLoad)
                    .collect(Collectors.joining(" / "));
            response.append("- ")
                    .append(entry.startedAt().format(HISTORY_DATE_FORMAT))
                    .append(": ")
                    .append(loads)
                    .append("\n");
        }
    }

    private static String formatLoad(String load) {
        if (load == null || load.isBlank()) {
            return "no load";
        }
        return TrainingDayMessageFormatter.escapeHtml(load);
    }

    private static String formatWeight(double weight) {
        return BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString();
    }
}
