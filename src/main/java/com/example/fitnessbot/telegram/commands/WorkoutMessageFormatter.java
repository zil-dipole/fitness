package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.service.ProgramService;
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
    private static final DateTimeFormatter HISTORY_DATE_FORMAT_RU = DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag("ru"));
    private static final Pattern SAVED_LOAD_PATTERN = Pattern.compile(
            "Saved (set|round) (\\d+): (.*?)(?:\\.\\s+(?:Next|Training)|\\.$|$).*",
            Pattern.CASE_INSENSITIVE
    );

    private WorkoutMessageFormatter() {
    }

    public static String formatExerciseResult(String message, WorkoutService.WorkoutExerciseView view) {
        return formatExerciseResult(message, view, UserLanguage.ENGLISH);
    }

    public static String formatExerciseResult(String message,
                                              WorkoutService.WorkoutExerciseView view,
                                              UserLanguage language) {
        return formatResultMessage(message, language) + "\n" + formatExerciseView(view, language);
    }

    public static String formatFinishScreen(String completionMessage,
                                            ProgramService.ActiveTrainingDayProgression progression) {
        return formatFinishScreen(completionMessage, progression, UserLanguage.ENGLISH);
    }

    public static String formatFinishScreen(String completionMessage,
                                            ProgramService.ActiveTrainingDayProgression progression,
                                            UserLanguage language) {
        StringBuilder response = new StringBuilder();
        boolean completedFiveWeeks = progression != null && progression.completedFiveWeeks();

        response.append(BotText.workoutFinishTitle(completedFiveWeeks, language));

        String result = formatCompletionMessage(completionMessage, language);
        if (result != null) {
            response.append("\n")
                    .append(result);
        }

        if (completedFiveWeeks) {
            response.append("\n").append(BotText.workoutFiveWeeksDone(language));
        }

        if (progression == null || progression.trainingDay() == null) {
            return response.toString();
        }

        response.append("\n\n")
                .append(BotText.workoutNextDay(trainingDayTitle(progression.trainingDay()), language))
                .append("\n")
                .append(BotText.workoutWeekReady(progression.weekNumber(), language))
                .append("\n")
                .append(BotText.workoutStartDayHint(language));

        return response.toString();
    }

    public static String formatExerciseView(WorkoutService.WorkoutExerciseView view) {
        return formatExerciseView(view, UserLanguage.ENGLISH);
    }

    public static String formatExerciseView(WorkoutService.WorkoutExerciseView view, UserLanguage language) {
        StringBuilder response = new StringBuilder();
        String stepLabel = stepLabel(view.circuit(), true, language);
        String promptLabel = stepLabel(view.circuit(), false, language);
        String note = cleanText(view.notes());
        String target = formatRepsOrDuration(view.repsOrDuration(), language);
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

        appendHistory(response, view.history(), language);
        response.append(BotText.workoutLoadPrompt(promptLabel, view.currentSetNumber(), language));

        return response.toString();
    }

    public static InlineKeyboardMarkup startDayKeyboard() {
        return startDayKeyboard(UserLanguage.ENGLISH);
    }

    public static InlineKeyboardMarkup startDayKeyboard(UserLanguage language) {
        InlineKeyboardButton startButton = new InlineKeyboardButton();
        startButton.setText(BotText.workoutStartDayButton(language));
        startButton.setCallbackData(START_ACTIVE_DAY_CALLBACK);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(startButton)));
        return markup;
    }

    public static InlineKeyboardMarkup exerciseKeyboard() {
        return exerciseKeyboard(null, UserLanguage.ENGLISH);
    }

    public static InlineKeyboardMarkup exerciseKeyboard(WorkoutService.WorkoutExerciseView view) {
        return exerciseKeyboard(view, UserLanguage.ENGLISH);
    }

    public static InlineKeyboardMarkup exerciseKeyboard(WorkoutService.WorkoutExerciseView view, UserLanguage language) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String previousLoad = previousLoadForButton(view, language);
        if (previousLoad != null) {
            InlineKeyboardButton previousWeightButton = new InlineKeyboardButton();
            previousWeightButton.setText(BotText.workoutUsePreviousButton(previousLoad, language));
            previousWeightButton.setCallbackData(PREVIOUS_WEIGHT_CALLBACK);
            rows.add(List.of(previousWeightButton));
        }

        InlineKeyboardButton noLoadButton = new InlineKeyboardButton();
        noLoadButton.setText(BotText.workoutNoLoadButton(language));
        noLoadButton.setCallbackData(NO_LOAD_CALLBACK);

        InlineKeyboardButton skipButton = new InlineKeyboardButton();
        skipButton.setText(BotText.workoutSkipButton(language));
        skipButton.setCallbackData(SKIP_EXERCISE_CALLBACK);

        InlineKeyboardButton finishButton = new InlineKeyboardButton();
        finishButton.setText(BotText.workoutFinishDayButton(language));
        finishButton.setCallbackData(FINISH_WORKOUT_CALLBACK);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        rows.add(List.of(noLoadButton));
        rows.add(List.of(skipButton, finishButton));
        markup.setKeyboard(rows);
        return markup;
    }

    private static void appendHistory(StringBuilder response,
                                      List<WorkoutService.WorkoutHistoryEntry> history,
                                      UserLanguage language) {
        if (history.isEmpty()) {
            return;
        }

        WorkoutService.WorkoutHistoryEntry entry = history.getFirst();
        String loads = entry.loads().stream()
                .map(load -> formatLoad(load, language))
                .collect(Collectors.joining(" / "));
        response.append(BotText.workoutHistoryPrefix(language))
                .append(entry.startedAt().format(BotText.isRussian(language) ? HISTORY_DATE_FORMAT_RU : HISTORY_DATE_FORMAT))
                .append(": ")
                .append(loads)
                .append("\n");
    }

    private static String formatResultMessage(String message, UserLanguage language) {
        if (message == null || message.isBlank()) {
            return BotText.workoutSavedDefault(language);
        }

        Matcher matcher = SAVED_LOAD_PATTERN.matcher(message.trim());
        if (matcher.matches()) {
            boolean circuit = "round".equalsIgnoreCase(matcher.group(1));
            String stepLabel = stepLabel(circuit, false, language);
            String stepNumber = matcher.group(2);
            String load = formatLoad(matcher.group(3).trim(), language);
            return BotText.workoutSavedLoad(load, stepLabel, stepNumber, language);
        }

        if (message.toLowerCase(Locale.ROOT).startsWith("skipped")) {
            return BotText.workoutSkipped(language);
        }

        return TrainingDayMessageFormatter.escapeHtml(message);
    }

    private static String formatRepsOrDuration(String repsOrDuration, UserLanguage language) {
        String value = cleanText(repsOrDuration);
        if (value == null) {
            return BotText.workoutPrescribedWork(language);
        }

        if (value.matches("\\d+(?:[.,]\\d+)?")) {
            return BotText.workoutReps(TrainingDayMessageFormatter.escapeHtml(value), language);
        }

        return TrainingDayMessageFormatter.escapeHtml(value);
    }

    private static String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().replaceAll("\\s+", " ");
    }

    private static String formatLoad(String load, UserLanguage language) {
        if (load == null || load.isBlank()) {
            return BotText.noLoadDisplay(language);
        }
        String normalized = load.trim().toLowerCase(Locale.ROOT);
        if ("no load".equals(normalized) || "none".equals(normalized)) {
            return BotText.noLoadDisplay(language);
        }
        return TrainingDayMessageFormatter.escapeHtml(load.trim());
    }

    private static String trainingDayTitle(TrainingDay trainingDay) {
        String title = cleanText(trainingDay.getTitle());
        if (title == null) {
            return "Training day";
        }
        return TrainingDayMessageFormatter.escapeHtml(title);
    }

    private static String previousLoadForButton(WorkoutService.WorkoutExerciseView view, UserLanguage language) {
        if (view == null) {
            return null;
        }
        if (view.previousLoad() != null && !view.previousLoad().isBlank()) {
            return truncateButtonValue(formatLoad(view.previousLoad(), language));
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

    private static String formatCompletionMessage(String message, UserLanguage language) {
        String result = cleanText(message);
        if (result == null) {
            return null;
        }
        if ("Training day finished.".equals(result)) {
            return BotText.workoutFinishedManually(language);
        }
        return formatResultMessage(result, language);
    }

    private static String stepLabel(boolean circuit, boolean capitalized, UserLanguage language) {
        return BotText.workoutStepLabel(circuit, capitalized, language);
    }
}
