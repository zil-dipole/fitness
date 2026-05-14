package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /menu command that displays the main menu with buttons
 */
@Component
public class MenuCommandHandler implements CommandHandler {

    public static final String COMMAND = "/menu";
    
    private final MenuKeyboardFactory menuKeyboardFactory;
    private final UserLanguageService languageService;

    @Autowired
    public MenuCommandHandler(MenuKeyboardFactory menuKeyboardFactory,
                              UserLanguageService languageService) {
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = languageService;
    }

    public MenuCommandHandler(MenuKeyboardFactory menuKeyboardFactory) {
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = null;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        var language = BotText.language(languageService, update.getMessage().getFrom().getId());
        sendMessage.setText(BotText.mainMenuTitle(language));
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
}
