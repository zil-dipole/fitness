package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.service.ProgramService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class StartProgramCallbackHandler implements CallbackQueryHandler {

    private static final String CALLBACK_PREFIX = "start_program:";

    private final ProgramService programService;

    public StartProgramCallbackHandler(ProgramService programService) {
        this.programService = programService;
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
            response.setText("Started program \"" + selection.program().getName() + "\".\n"
                    + "Active training day: " + selection.trainingDay().getTitle() + "\n\n"
                    + "Use /active_day to view it.");
        } catch (ProgramException e) {
            response.setText(e.getMessage());
        }

        return response;
    }
}
