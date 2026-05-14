package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class LanguageCommandHandler implements CommandHandler {

    public static final String COMMAND = "/language";
    public static final String CALLBACK_PREFIX = "language:";

    private final UserLanguageService languageService;
    private final MenuKeyboardFactory menuKeyboardFactory;

    public LanguageCommandHandler(UserLanguageService languageService, MenuKeyboardFactory menuKeyboardFactory) {
        this.languageService = languageService;
        this.menuKeyboardFactory = menuKeyboardFactory;
    }

    @Override
    public boolean canHandle(String command) {
        return command != null && (COMMAND.equals(command) || command.startsWith(COMMAND + " "));
    }

    @Override
    public SendMessage handle(Update update) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        String selector = extractSelector(update.getMessage().getText());
        UserLanguage currentLanguage = languageService.getLanguage(telegramUserId);

        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());

        if (selector == null) {
            response.setText(BotText.languagePrompt(currentLanguage));
            response.setReplyMarkup(languageKeyboard());
            return response;
        }

        return UserLanguage.fromCode(selector)
                .map(language -> {
                    UserLanguage savedLanguage = languageService.setLanguage(telegramUserId, language);
                    response.setText(BotText.languageChanged(savedLanguage));
                    response.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(telegramUserId));
                    return response;
                })
                .orElseGet(() -> {
                    response.setText(BotText.invalidLanguage(currentLanguage));
                    response.setReplyMarkup(languageKeyboard());
                    return response;
                });
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return BotText.commandDescription(COMMAND, null);
    }

    static InlineKeyboardMarkup languageKeyboard() {
        InlineKeyboardButton englishButton = new InlineKeyboardButton();
        englishButton.setText(BotText.englishButton());
        englishButton.setCallbackData(CALLBACK_PREFIX + UserLanguage.ENGLISH.getCode());

        InlineKeyboardButton russianButton = new InlineKeyboardButton();
        russianButton.setText(BotText.russianButton());
        russianButton.setCallbackData(CALLBACK_PREFIX + UserLanguage.RUSSIAN.getCode());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(englishButton, russianButton)));
        return markup;
    }

    private String extractSelector(String command) {
        if (command == null) {
            return null;
        }

        String[] parts = command.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            return null;
        }
        return parts[1].trim();
    }
}
