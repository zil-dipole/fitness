package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class ActiveDayCommandHandler implements CommandHandler {

    public static final String COMMAND = "/active_day";

    private final ProgramService programService;

    public ActiveDayCommandHandler(ProgramService programService) {
        this.programService = programService;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Transactional(readOnly = true)
    @Override
    public SendMessage handle(Update update) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        TrainingDay activeTrainingDay = programService.getActiveTrainingDayForUser(telegramUserId);

        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());
        if (activeTrainingDay == null) {
            response.setText("You don't have an active program. Open a saved program and press Start Program.");
            return response;
        }

        response.setText("Active training day:\n\n" + TrainingDayMessageFormatter.format(activeTrainingDay));
        response.setParseMode("HTML");
        response.setReplyMarkup(WorkoutMessageFormatter.startDayKeyboard());
        return response;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Show active training day";
    }
}
