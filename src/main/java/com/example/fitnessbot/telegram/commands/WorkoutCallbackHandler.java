package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.WorkoutService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class WorkoutCallbackHandler implements CallbackQueryHandler {

    private final WorkoutService workoutService;
    private final ProgramService programService;

    public WorkoutCallbackHandler(WorkoutService workoutService, ProgramService programService) {
        this.workoutService = workoutService;
        this.programService = programService;
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
            if (WorkoutMessageFormatter.START_ACTIVE_DAY_CALLBACK.equals(data)) {
                WorkoutService.WorkoutExerciseView view = workoutService.startActiveTrainingDay(telegramUserId);
                response.setText(WorkoutMessageFormatter.formatExerciseView(view));
                response.setParseMode("HTML");
                response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard(view));
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
                response.setText("You don't have an active workout session.");
                return response;
            }

            appendNextTrainingDay(response, telegramUserId, "Training day finished.");
            return response;
        } catch (WorkoutException e) {
            response.setText(e.getMessage());
            return response;
        }
    }

    private void applyWorkoutResult(SendMessage response, WorkoutService.WeightEntryResult result, Long telegramUserId) {
        if (!result.accepted()) {
            response.setText(result.message());
            return;
        }

        if (result.dayCompleted()) {
            appendNextTrainingDay(response, telegramUserId, result.message());
            return;
        }

        response.setText(WorkoutMessageFormatter.formatExerciseResult(result.message(), result.exerciseView()));
        response.setParseMode("HTML");
        response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard(result.exerciseView()));
    }

    private void appendNextTrainingDay(SendMessage response, Long telegramUserId, String completionMessage) {
        ProgramService.ActiveTrainingDayProgression progression = programService.advanceActiveTrainingDayForUser(telegramUserId);
        response.setText(WorkoutMessageFormatter.formatFinishScreen(completionMessage, progression));
        response.setParseMode("HTML");
        if (progression != null && progression.trainingDay() != null) {
            response.setReplyMarkup(WorkoutMessageFormatter.startDayKeyboard());
        }
    }
}
