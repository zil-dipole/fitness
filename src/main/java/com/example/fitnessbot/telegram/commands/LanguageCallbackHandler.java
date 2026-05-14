package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class LanguageCallbackHandler implements CallbackQueryHandler {

    private final UserLanguageService languageService;
    private final MenuKeyboardFactory menuKeyboardFactory;

    public LanguageCallbackHandler(UserLanguageService languageService, MenuKeyboardFactory menuKeyboardFactory) {
        this.languageService = languageService;
        this.menuKeyboardFactory = menuKeyboardFactory;
    }

    @Override
    public boolean canHandle(CallbackQuery callbackQuery) {
        return callbackQuery.getData() != null
                && callbackQuery.getData().startsWith(LanguageCommandHandler.CALLBACK_PREFIX);
    }

    @Override
    public SendMessage handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long telegramUserId = callbackQuery.getFrom().getId();
        UserLanguage currentLanguage = languageService.getLanguage(telegramUserId);

        SendMessage response = new SendMessage();
        response.setChatId(callbackQuery.getMessage().getChatId().toString());

        String selector = callbackQuery.getData().substring(LanguageCommandHandler.CALLBACK_PREFIX.length());
        UserLanguage.fromCode(selector).ifPresentOrElse(language -> {
            UserLanguage savedLanguage = languageService.setLanguage(telegramUserId, language);
            response.setText(BotText.languageChanged(savedLanguage));
            response.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(telegramUserId));
        }, () -> {
            response.setText(BotText.invalidLanguage(currentLanguage));
            response.setReplyMarkup(LanguageCommandHandler.languageKeyboard());
        });

        return response;
    }
}
