package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.service.WorkoutService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class WorkoutCallbackHandler implements CallbackQueryHandler {

    private final WorkoutService workoutService;

    public WorkoutCallbackHandler(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @Override
    public boolean canHandle(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        return WorkoutMessageFormatter.START_ACTIVE_DAY_CALLBACK.equals(data)
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
                response.setText("Training day started.\n\n" + WorkoutMessageFormatter.formatExerciseView(view));
                response.setParseMode("HTML");
                response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard());
                return response;
            }

            if (WorkoutMessageFormatter.SKIP_EXERCISE_CALLBACK.equals(data)) {
                WorkoutService.WeightEntryResult result = workoutService.skipCurrentExercise(telegramUserId);
                applyWorkoutResult(response, result);
                return response;
            }

            boolean finished = workoutService.finishActiveWorkout(telegramUserId);
            response.setText(finished ? "Training day finished." : "You don't have an active workout session.");
            return response;
        } catch (WorkoutException e) {
            response.setText(e.getMessage());
            return response;
        }
    }

    private void applyWorkoutResult(SendMessage response, WorkoutService.WeightEntryResult result) {
        if (result.dayCompleted()) {
            response.setText(result.message());
            return;
        }

        response.setText(result.message() + "\n\n" + WorkoutMessageFormatter.formatExerciseView(result.exerciseView()));
        response.setParseMode("HTML");
        response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard());
    }
}
