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
        sendMessage.setText("You're already using the bot! Send /menu to see available options.");
        return sendMessage;
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("Welcome to Fitness Bot! Forward your workout programs to me and I'll parse and save them for you.\n\nChoose an option below:");
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