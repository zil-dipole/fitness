package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.UserLanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class ActiveDayCommandHandler implements CommandHandler {

    public static final String COMMAND = "/active_day";

    private final ProgramService programService;
    private final UserLanguageService languageService;

    @Autowired
    public ActiveDayCommandHandler(ProgramService programService,
                                   UserLanguageService languageService) {
        this.programService = programService;
        this.languageService = languageService;
    }

    public ActiveDayCommandHandler(ProgramService programService) {
        this.programService = programService;
        this.languageService = null;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Transactional(readOnly = true)
    @Override
    public SendMessage handle(Update update) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        var language = BotText.language(languageService, telegramUserId);
        TrainingDay activeTrainingDay = programService.getActiveTrainingDayForUser(telegramUserId);
        int weekNumber = programService.getActiveProgramWeekForUser(telegramUserId);

        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());
        if (activeTrainingDay == null) {
            response.setText(BotText.activeProgramMissing(language));
            return response;
        }

        response.setText(BotText.activeDayHeader(weekNumber, language)
                + TrainingDayMessageFormatter.format(activeTrainingDay, language));
        response.setParseMode("HTML");
        response.setReplyMarkup(WorkoutMessageFormatter.startDayKeyboard(language));
        return response;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return BotText.commandDescription(COMMAND, null);
    }
}
