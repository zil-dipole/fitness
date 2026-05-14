package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.service.WorkoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class WorkoutCallbackHandler implements CallbackQueryHandler {

    private final WorkoutService workoutService;
    private final ProgramService programService;
    private final UserLanguageService languageService;

    @Autowired
    public WorkoutCallbackHandler(WorkoutService workoutService,
                                  ProgramService programService,
                                  UserLanguageService languageService) {
        this.workoutService = workoutService;
        this.programService = programService;
        this.languageService = languageService;
    }

    public WorkoutCallbackHandler(WorkoutService workoutService, ProgramService programService) {
        this.workoutService = workoutService;
        this.programService = programService;
        this.languageService = null;
    }

    @Override
    public boolean canHandle(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        return WorkoutMessageFormatter.START_ACTIVE_DAY_CALLBACK.equals(data)
                || WorkoutMessageFormatter.PREVIOUS_WEIGHT_CALLBACK.equals(data)
                || WorkoutMessageFormatter.NO_LOAD_CALLBACK.equals(data)
                || WorkoutMessageFormatter.SKIP_EXERCISE_CALLBACK.equals(data)
                || WorkoutMessageFormatter.FINISH_WORKOUT_CALLBACK.equals(data);
    }

    @Override
    public SendMessage handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        SendMessage response = new SendMessage();
        response.setChatId(callbackQuery.getMessage().getChatId().toString());

        try {
            String data = callbackQuery.getData();
            Long telegramUserId = callbackQuery.getFrom().getId();
            var language = BotText.language(languageService, telegramUserId);
            if (WorkoutMessageFormatter.START_ACTIVE_DAY_CALLBACK.equals(data)) {
                WorkoutService.WorkoutExerciseView view = workoutService.startActiveTrainingDay(telegramUserId);
                response.setText(WorkoutMessageFormatter.formatExerciseView(view, language));
                response.setParseMode("HTML");
                response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard(view, language));
                return response;
            }

            if (WorkoutMessageFormatter.PREVIOUS_WEIGHT_CALLBACK.equals(data)) {
                WorkoutService.WeightEntryResult result = workoutService.recordPreviousWeightForCurrentSet(telegramUserId);
                applyWorkoutResult(response, result, telegramUserId);
                return response;
            }

            if (WorkoutMessageFormatter.SKIP_EXERCISE_CALLBACK.equals(data)) {
                WorkoutService.WeightEntryResult result = workoutService.skipCurrentExercise(telegramUserId);
                applyWorkoutResult(response, result, telegramUserId);
                return response;
            }

            if (WorkoutMessageFormatter.NO_LOAD_CALLBACK.equals(data)) {
                WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(telegramUserId, "none");
                applyWorkoutResult(response, result, telegramUserId);
                return response;
            }

            boolean finished = workoutService.finishActiveWorkout(telegramUserId);
            if (!finished) {
                response.setText(BotText.workoutNoActiveSession(language));
                return response;
            }

            appendNextTrainingDay(response, telegramUserId, "Training day finished.");
            return response;
        } catch (WorkoutException e) {
            var language = BotText.language(languageService, callbackQuery.getFrom().getId());
            response.setText(localizeWorkoutServiceMessage(e.getMessage(), language));
            return response;
        }
    }

    private void applyWorkoutResult(SendMessage response, WorkoutService.WeightEntryResult result, Long telegramUserId) {
        var language = BotText.language(languageService, telegramUserId);
        if (!result.accepted()) {
            response.setText(localizeWorkoutServiceMessage(result.message(), language));
            return;
        }

        if (result.dayCompleted()) {
            appendNextTrainingDay(response, telegramUserId, result.message());
            return;
        }

        response.setText(WorkoutMessageFormatter.formatExerciseResult(result.message(), result.exerciseView(), language));
        response.setParseMode("HTML");
        response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard(result.exerciseView(), language));
    }

    private void appendNextTrainingDay(SendMessage response, Long telegramUserId, String completionMessage) {
        var language = BotText.language(languageService, telegramUserId);
        ProgramService.ActiveTrainingDayProgression progression = programService.advanceActiveTrainingDayForUser(telegramUserId);
        response.setText(WorkoutMessageFormatter.formatFinishScreen(completionMessage, progression, language));
        response.setParseMode("HTML");
        if (progression != null && progression.trainingDay() != null) {
            response.setReplyMarkup(WorkoutMessageFormatter.startDayKeyboard(language));
        }
    }

    private String localizeWorkoutServiceMessage(String message, com.example.fitnessbot.model.UserLanguage language) {
        if ("No previous load is available for this exercise.".equals(message)) {
            return BotText.workoutNoPreviousLoad(language);
        }
        if ("You don't have an active workout session.".equals(message)) {
            return BotText.workoutNoActiveSession(language);
        }
        if ("You don't have an active training day.".equals(message)) {
            return BotText.workoutNoActiveTrainingDay(language);
        }
        if ("Active training day has no exercises.".equals(message)) {
            return BotText.workoutTrainingDayNoExercises(language);
        }
        if ("Current workout exercise is missing.".equals(message)) {
            return BotText.workoutCurrentExerciseMissing(language);
        }
        if (message != null && message.startsWith("👉 Send load for this set")) {
            return BotText.workoutLoadPrompt(BotText.workoutStepLabel(false, false, language), 1, language);
        }
        return message;
    }
}
