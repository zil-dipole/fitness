package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.WorkoutService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class StartProgramCallbackHandler implements CallbackQueryHandler {

    private static final String CALLBACK_PREFIX = "start_program:";

    private final ProgramService programService;
    private final WorkoutService workoutService;

    public StartProgramCallbackHandler(ProgramService programService, WorkoutService workoutService) {
        this.programService = programService;
        this.workoutService = workoutService;
    }

    @Override
    public boolean canHandle(CallbackQuery callbackQuery) {
        return callbackQuery.getData() != null && callbackQuery.getData().startsWith(CALLBACK_PREFIX);
    }

    @Override
    public SendMessage handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        SendMessage response = new SendMessage();
        response.setChatId(callbackQuery.getMessage().getChatId().toString());

        Long programId;
        try {
            programId = Long.parseLong(callbackQuery.getData().substring(CALLBACK_PREFIX.length()));
        } catch (NumberFormatException e) {
            response.setText("Invalid program ID.");
            return response;
        }

        try {
            ProgramService.ActiveProgramSelection selection =
                    programService.startProgramForUser(programId, callbackQuery.getFrom().getId());
            WorkoutService.WorkoutExerciseView view = workoutService.startActiveTrainingDay(callbackQuery.getFrom().getId());
            response.setText("✅ <b>" + TrainingDayMessageFormatter.escapeHtml(selection.program().getName())
                    + "</b> started\n" + WorkoutMessageFormatter.formatExerciseView(view));
            response.setParseMode("HTML");
            response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard(view));
        } catch (ProgramException | WorkoutException e) {
            response.setText(e.getMessage());
        }

        return response;
    }
}
