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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorkoutMessageFormatter {

    public static final String START_ACTIVE_DAY_CALLBACK = "start_active_day";
    public static final String PREVIOUS_WEIGHT_CALLBACK = "previous_weight";
    public static final String NO_LOAD_CALLBACK = "none_load";
    public static final String SKIP_EXERCISE_CALLBACK = "skip_workout_exercise";
    public static final String FINISH_WORKOUT_CALLBACK = "finish_workout";

    private static final DateTimeFormatter HISTORY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final Pattern SAVED_LOAD_PATTERN = Pattern.compile(
            "Saved (set|round) (\\d+): (.*?)(?:\\.\\s+(?:Next|Training)|\\.$|$).*",
            Pattern.CASE_INSENSITIVE
    );

    private WorkoutMessageFormatter() {
    }

    public static String formatExerciseResult(String message, WorkoutService.WorkoutExerciseView view) {
        return formatResultMessage(message) + "\n" + formatExerciseView(view);
    }

    public static String formatExerciseView(WorkoutService.WorkoutExerciseView view) {
        StringBuilder response = new StringBuilder();
        String stepLabel = view.circuit() ? "Round" : "Set";
        String promptLabel = view.circuit() ? "round" : "set";
        String note = cleanText(view.notes());
        String target = formatRepsOrDuration(view.repsOrDuration());
        if (note != null && note.toLowerCase(Locale.ROOT).startsWith("rpe")) {
            target = target + " @ " + TrainingDayMessageFormatter.escapeHtml(note);
            note = null;
        }

        response.append("🔥 <b>")
                .append(TrainingDayMessageFormatter.escapeHtml(view.exerciseName()))
                .append("</b>")
                .append("\n");
        response.append(stepLabel)
                .append(" ")
                .append(view.currentSetNumber())
                .append("/")
                .append(view.totalSets())
                .append(" → <b>")
                .append(target)
                .append("</b>")
                .append("\n");
        if (note != null) {
            response.append("• ")
                    .append(TrainingDayMessageFormatter.escapeHtml(note))
                    .append("\n");
        }

        if (!view.videoUrls().isEmpty()) {
            response.append("🎥 ")
                    .append(TrainingDayMessageFormatter.escapeHtml(view.videoUrls().getFirst()))
                    .append("\n");
        }

        appendHistory(response, view.history());
        response.append("👉 <b>Load for ")
                .append(promptLabel)
                .append(" ")
                .append(view.currentSetNumber())
                .append(":</b> ")
                .append("60 · red band · bodyweight · none");

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

        String previousLoad = previousLoadForButton(view);
        if (previousLoad != null) {
            InlineKeyboardButton previousWeightButton = new InlineKeyboardButton();
            previousWeightButton.setText("Use " + previousLoad);
            previousWeightButton.setCallbackData(PREVIOUS_WEIGHT_CALLBACK);
            rows.add(List.of(previousWeightButton));
        }

        InlineKeyboardButton noLoadButton = new InlineKeyboardButton();
        noLoadButton.setText("No load");
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
            return;
        }

        WorkoutService.WorkoutHistoryEntry entry = history.getFirst();
        String loads = entry.loads().stream()
                .map(WorkoutMessageFormatter::formatLoad)
                .collect(Collectors.joining(" / "));
        response.append("Last ")
                .append(entry.startedAt().format(HISTORY_DATE_FORMAT))
                .append(": ")
                .append(loads)
                .append("\n");
    }

    private static String formatResultMessage(String message) {
        if (message == null || message.isBlank()) {
            return "✅ Saved";
        }

        Matcher matcher = SAVED_LOAD_PATTERN.matcher(message.trim());
        if (matcher.matches()) {
            String stepLabel = matcher.group(1).toLowerCase(Locale.ROOT);
            String stepNumber = matcher.group(2);
            String load = matcher.group(3).trim();
            return "✅ <b>" + TrainingDayMessageFormatter.escapeHtml(load) + " saved</b> · " + stepLabel + " " + stepNumber;
        }

        if (message.toLowerCase(Locale.ROOT).startsWith("skipped")) {
            return "⏭ <b>Skipped</b>";
        }

        return TrainingDayMessageFormatter.escapeHtml(message);
    }

    private static String formatRepsOrDuration(String repsOrDuration) {
        String value = cleanText(repsOrDuration);
        if (value == null) {
            return "do prescribed work";
        }

        if (value.matches("\\d+(?:[.,]\\d+)?")) {
            return TrainingDayMessageFormatter.escapeHtml(value) + " reps";
        }

        return TrainingDayMessageFormatter.escapeHtml(value);
    }

    private static String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().replaceAll("\\s+", " ");
    }

    private static String formatLoad(String load) {
        if (load == null || load.isBlank()) {
            return "no load";
        }
        return TrainingDayMessageFormatter.escapeHtml(load);
    }

    private static String previousLoadForButton(WorkoutService.WorkoutExerciseView view) {
        if (view == null) {
            return null;
        }
        if (view.previousLoad() != null && !view.previousLoad().isBlank()) {
            return truncateButtonValue(view.previousLoad().trim());
        }
        if (view.previousWeightKg() != null && view.previousWeightKg() > 0) {
            return formatWeight(view.previousWeightKg()) + " kg";
        }
        return null;
    }

    private static String truncateButtonValue(String value) {
        if (value.length() <= 48) {
            return value;
        }
        return value.substring(0, 45) + "...";
    }

    private static String formatWeight(double weight) {
        return BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString();
    }
}
