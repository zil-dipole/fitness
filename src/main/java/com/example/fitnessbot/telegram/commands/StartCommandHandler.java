package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
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

    public StartCommandHandler(MenuKeyboardFactory menuKeyboardFactory) {
        this.menuKeyboardFactory = menuKeyboardFactory;
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
        sendMessage.setText("You're already set up.\n\nSend /menu to see what you can do next.");
        return sendMessage;
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("""
                Welcome to Fitness Bot.

                Here is the easiest way to use it:
                1. Create a program or open a saved one.
                2. Forward each training day message you want to keep.
                3. Open /active_day when you're ready to train.

                Choose an option below to get started.
                """);
        sendMessage.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(update.getMessage().getFrom().getId()));
        return sendMessage;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Start bot";
    }
}
