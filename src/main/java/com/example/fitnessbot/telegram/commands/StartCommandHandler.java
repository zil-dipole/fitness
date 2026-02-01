package com.example.fitnessbot.telegram.commands;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /start command
 */
@Component
public class StartCommandHandler implements CommandHandler {
    
    @Override
    public boolean canHandle(String command) {
        return "/start".equals(command);
    }
    
    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("Welcome to Fitness Bot! Forward your workout programs to me and I'll parse and save them for you.");
        return sendMessage;
    }
}