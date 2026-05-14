package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Handler for the /start command
 */
@Component
public class StartCommandHandler implements ContextAwareCommandHandler {

    public static final String COMMAND = "/start";
    
    private final MenuKeyboardFactory menuKeyboardFactory;
    private final UserLanguageService languageService;

    @Autowired
    public StartCommandHandler(MenuKeyboardFactory menuKeyboardFactory,
                               UserLanguageService languageService) {
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = languageService;
    }

    public StartCommandHandler(MenuKeyboardFactory menuKeyboardFactory) {
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = null;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public boolean isAvailable(Long userId, ProgramCreationSessionManager sessionManager) {
        // Start command is available only if user doesn't have an active session
        // This prevents suggesting /start to users who are already in a workflow
        return !sessionManager.hasActiveSession(userId);
    }

    @Override
    public SendMessage handleUnavailable(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        var language = BotText.language(languageService, telegramUserId(update));
        sendMessage.setText(BotText.startAlreadySetup(language));
        return sendMessage;
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        var language = BotText.language(languageService, update.getMessage().getFrom().getId());
        sendMessage.setText(BotText.startWelcome(language));
        sendMessage.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(update.getMessage().getFrom().getId()));
        return sendMessage;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return BotText.commandDescription(COMMAND, null);
    }

    private Long telegramUserId(Update update) {
        if (update == null || update.getMessage() == null || update.getMessage().getFrom() == null) {
            return null;
        }
        return update.getMessage().getFrom().getId();
    }
}
