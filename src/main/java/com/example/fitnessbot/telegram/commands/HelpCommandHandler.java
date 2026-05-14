package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Comparator;
import java.util.List;

/**
 * Handler for the /help command
 */
@Component
public class HelpCommandHandler implements CommandHandler {

    public static final String COMMAND = "/help";
    private final CommandRegistryService commandRegistryService;
    private final MenuKeyboardFactory menuKeyboardFactory;
    private final UserLanguageService languageService;

    @Autowired
    public HelpCommandHandler(CommandRegistryService commandRegistryService,
                              MenuKeyboardFactory menuKeyboardFactory,
                              UserLanguageService languageService) {
        this.commandRegistryService = commandRegistryService;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = languageService;
    }

    public HelpCommandHandler(CommandRegistryService commandRegistryService,
                              MenuKeyboardFactory menuKeyboardFactory) {
        this.commandRegistryService = commandRegistryService;
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

        List<CommandMetadata> commands = commandRegistryService.getAllCommands().stream()
                .sorted(Comparator.comparing(CommandMetadata::getCommand))
                .toList();

        sendMessage.setText(BotText.helpText(language, commands));
        sendMessage.setParseMode("HTML");
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
