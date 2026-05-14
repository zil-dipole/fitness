package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class DeleteProgramCallbackHandler implements CallbackQueryHandler {

    private static final String CALLBACK_PREFIX = "delete_program:";

    private final ProgramService programService;
    private final MenuKeyboardFactory menuKeyboardFactory;
    private final UserLanguageService languageService;

    @Autowired
    public DeleteProgramCallbackHandler(ProgramService programService,
                                        MenuKeyboardFactory menuKeyboardFactory,
                                        UserLanguageService languageService) {
        this.programService = programService;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = languageService;
    }

    public DeleteProgramCallbackHandler(ProgramService programService, MenuKeyboardFactory menuKeyboardFactory) {
        this.programService = programService;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = null;
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
        var language = BotText.language(languageService, callbackQuery.getFrom().getId());

        Long programId;
        try {
            programId = Long.parseLong(callbackQuery.getData().substring(CALLBACK_PREFIX.length()));
        } catch (NumberFormatException e) {
            response.setText(BotText.invalidProgramId(language));
            return response;
        }

        boolean deleted = programService.deleteProgramForUser(programId, callbackQuery.getFrom().getId());
        if (deleted) {
            response.setText(BotText.programDeleted(language));
            response.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(callbackQuery.getFrom().getId()));
        } else {
            response.setText(BotText.programNotFound(language));
        }

        return response;
    }
}
